#!/usr/bin/env python3
"""Online single-schema backup; restore only to a labelled, empty isolated MySQL container.
Credentials stay inside the container environment. Backups contain private application data.
"""
import argparse
import datetime as dt
import gzip
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import re
import shutil
import subprocess
import sys
import tarfile
import tempfile

DB_COMMAND = '''set -eu
: "${MYSQL_USER:?A dedicated MYSQL_USER is required}"
: "${MYSQL_PASSWORD:?MYSQL_PASSWORD is required}"
: "${MYSQL_DATABASE:?MYSQL_DATABASE is required}"
[ "$MYSQL_USER" != root ] && [ "$MYSQL_DATABASE" = "$1" ] || exit 65
export MYSQL_PWD="$MYSQL_PASSWORD"
'''
DUMP = DB_COMMAND + '''exec mysqldump --user="$MYSQL_USER" --single-transaction --quick --no-tablespaces --skip-add-locks --hex-blob --set-gtid-purged=OFF --column-statistics=0 "$1"'''
IMPORT = DB_COMMAND + '''exec mysql --user="$MYSQL_USER" --binary-mode=1 --database="$1"'''
COUNT = DB_COMMAND + '''exec mysql --user="$MYSQL_USER" --batch --skip-column-names --database="$1" -e 'SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()' '''

def command(container, db, script):
    return ['docker', 'exec', '-i', container, 'sh', '-c', script, 'webox-storage', db]

def digest(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest() if hasattr(hashlib, 'file_digest') else legacy_digest(stream)

def legacy_digest(stream):
    value = hashlib.sha256()
    for data in iter(lambda: stream.read(1024 * 1024), b''):
        value.update(data)
    return value.hexdigest()

def backup(args):
    media = args.media_root.resolve(strict=True)
    if not media.is_dir() or media == Path('/'):
        raise ValueError('MEDIA_ROOT must be a dedicated directory.')
    target = args.output.resolve()
    if target == media or media in target.parents:
        raise ValueError('The backup directory must be outside MEDIA_ROOT.')
    target.mkdir(mode=0o700, parents=True, exist_ok=False)
    database = target / 'database.sql.gz'
    process = subprocess.Popen(command(args.container, args.database, DUMP), stdout=subprocess.PIPE, stderr=subprocess.DEVNULL)
    try:
        with gzip.open(database, 'wb', compresslevel=6) as output:
            shutil.copyfileobj(process.stdout, output)
        if process.wait() != 0:
            raise RuntimeError('MySQL export failed. Check the container, schema and dedicated account privileges.')
    finally:
        process.stdout.close()
        if process.poll() is None:
            process.kill()
            process.wait()
    count = 0
    archive = target / 'media.tar.gz'
    with tarfile.open(archive, 'w:gz', dereference=False) as bundle:
        for directory, subdirectories, names in os.walk(media, followlinks=False):
            subdirectories[:] = [name for name in subdirectories if not (Path(directory) / name).is_symlink()]
            for name in sorted(names):
                path = Path(directory) / name
                if path.is_symlink() or not path.is_file():
                    continue
                try:
                    bundle.add(path, arcname=str(path.relative_to(media)), recursive=False)
                    count += 1
                except FileNotFoundError:
                    # Maintenance may remove an unregistered remnant; registered immutable media is retained.
                    continue
    manifest = {'format': 1, 'createdAt': dt.datetime.now(dt.timezone.utc).isoformat(), 'database': args.database, 'mediaFiles': count,
                'files': {path.name: {'sha256': digest(path), 'bytes': path.stat().st_size} for path in [database, archive]}}
    (target / 'manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
    print(json.dumps({'backup': str(target), 'mediaFiles': count, 'complete': True}))

def restore(args):
    if not args.database.startswith('webox_restore_'):
        raise ValueError('Restore requires a database named webox_restore_*.')
    label = subprocess.run(['docker', 'inspect', '--format', '{{ index .Config.Labels "webox.restore-target" }}', args.container], capture_output=True, text=True, check=True).stdout.strip()
    if label != 'true':
        raise ValueError('Restore requires the container label webox.restore-target=true.')
    source = args.source.resolve(strict=True)
    manifest = json.loads((source / 'manifest.json').read_text())
    if manifest.get('format') != 1:
        raise ValueError('Unsupported backup format.')
    for name in ['database.sql.gz', 'media.tar.gz']:
        file = source / name
        if file.is_symlink() or not file.is_file() or digest(file) != manifest['files'][name]['sha256'] or file.stat().st_size != manifest['files'][name]['bytes']:
            raise ValueError('Backup checksum verification failed.')
    media = args.media_root.resolve()
    if media == Path('/') or media == source or media in source.parents or source in media.parents:
        raise ValueError('Restore media must use a separate empty directory.')
    media.mkdir(mode=0o700, parents=True, exist_ok=True)
    if any(media.iterdir()):
        raise ValueError('Restore media directory must be empty.')
    check = subprocess.run(command(args.container, args.database, COUNT), capture_output=True, text=True)
    if check.returncode or check.stdout.strip() != '0':
        raise ValueError('Restore database must exist, match MYSQL_DATABASE, use a non-root account and contain no tables.')
    stage = Path(tempfile.mkdtemp(prefix='.webox-restore-', dir=media.parent))
    try:
        total = 0
        with tarfile.open(source / 'media.tar.gz', 'r|gz') as bundle:
            for member in bundle:
                relative = PurePosixPath(member.name)
                if relative.is_absolute() or '..' in relative.parts or not relative.parts or not (member.isfile() or member.isdir()):
                    raise ValueError('Unsafe media archive entry.')
                if member.size < 0 or member.size > 5_242_880:
                    raise ValueError('Media archive file exceeds the application size limit.')
                total += member.size
                if total > 10 * 1024 ** 3:
                    raise ValueError('Media archive exceeds the 10 GiB restore limit.')
                destination = stage.joinpath(*relative.parts)
                if member.isdir():
                    destination.mkdir(mode=0o700, parents=True, exist_ok=True)
                else:
                    destination.parent.mkdir(mode=0o700, parents=True, exist_ok=True)
                    with bundle.extractfile(member) as data, destination.open('xb') as output:
                        shutil.copyfileobj(data, output)
        process = subprocess.Popen(command(args.container, args.database, IMPORT), stdin=subprocess.PIPE, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        try:
            with gzip.open(source / 'database.sql.gz', 'rb') as data:
                shutil.copyfileobj(data, process.stdin)
            process.stdin.close()
            if process.wait() != 0:
                raise RuntimeError('MySQL restore failed. Keep this target isolated and inspect it before another attempt.')
        finally:
            if process.poll() is None:
                process.kill()
                process.wait()
        media.rmdir()
        stage.rename(media)
        print(json.dumps({'restoredDatabase': args.database, 'mediaRoot': str(media), 'isolated': True, 'complete': True}))
    finally:
        if stage.exists():
            shutil.rmtree(stage)

def main():
    os.umask(0o077)
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('mode', choices=['backup', 'restore'])
    parser.add_argument('--container', required=True)
    parser.add_argument('--database', required=True)
    parser.add_argument('--media-root', type=Path, required=True)
    parser.add_argument('--output', type=Path)
    parser.add_argument('--from-backup', dest='source', type=Path)
    args = parser.parse_args()
    if not re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9_.-]{0,127}', args.container) or not re.fullmatch(r'[A-Za-z][A-Za-z0-9_]{0,63}', args.database):
        parser.error('Invalid container or database name.')
    if args.mode == 'backup' and args.output is None or args.mode == 'restore' and args.source is None:
        parser.error('Backup requires --output; restore requires --from-backup.')
    try:
        (backup if args.mode == 'backup' else restore)(args)
    except (OSError, ValueError, RuntimeError, subprocess.SubprocessError, tarfile.TarError, KeyError) as error:
        # Never emit SQL, environment values, credentials or archive contents.
        print(f'Storage operation failed: {type(error).__name__}. Check the supplied paths, schema, isolation label and container access. Incomplete outputs are not valid backups.', file=sys.stderr)
        return 1
    return 0

if __name__ == '__main__':
    sys.exit(main())
