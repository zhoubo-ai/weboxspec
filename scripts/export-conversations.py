#!/usr/bin/env python3
"""Copy complete native Codex JSONL records for this workspace into a private, ignored archive.

Run again after the final response to include the end of the active conversation. This does not
claim to export other products, inaccessible sessions, or records that have not been written yet.
"""
import hashlib
import json
import os
import argparse
import time
from datetime import datetime, timezone
from pathlib import Path

os.umask(0o077)
workspace = Path(__file__).resolve().parents[1]
source_root = Path(os.environ.get('CODEX_HOME', str(Path.home() / '.codex'))) / 'sessions'
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--after-current-turn', action='store_true', help='Wait for the next native task_complete record before exporting, including the final response.')
args = parser.parse_args()
completed_turn = None
if args.after_current_turn:
    matching = []
    for source in source_root.rglob('*.jsonl'):
        with source.open('rb') as stream:
            try:
                metadata = json.loads(stream.readline()).get('payload', {})
            except (ValueError, AttributeError):
                continue
        if metadata.get('cwd') == str(workspace):
            matching.append(source)
    if not matching:
        raise SystemExit('No native session for this workspace is available.')
    active = max(matching, key=lambda path: path.stat().st_mtime_ns)
    print('Waiting for the active workspace session to write its next task_complete record.', flush=True)
    with active.open('rb') as stream:
        stream.seek(0, os.SEEK_END)
        end = stream.tell()
        if end:
            stream.seek(end - 1)
            discard_partial = stream.read(1) != b'\n'
        else:
            discard_partial = False
        pending = b''
        while completed_turn is None:
            chunk = stream.read()
            if not chunk:
                time.sleep(0.25)
                continue
            pending += chunk
            while b'\n' in pending:
                line, pending = pending.split(b'\n', 1)
                if discard_partial:
                    discard_partial = False
                    continue
                record = json.loads(line)
                payload = record.get('payload', {})
                if record.get('type') == 'event_msg' and payload.get('type') == 'task_complete':
                    completed_turn = {'nativeSourcePath': str(active), 'timestamp': record.get('timestamp'), 'nativeEvent': 'task_complete'}
                    break
archive = workspace / 'ai-conversations'
archive.mkdir(mode=0o700, exist_ok=True)
archive.chmod(0o700)
stamp = datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S.%fZ')
target = archive / stamp
target.mkdir(mode=0o700)
entries = []
for source in sorted(source_root.rglob('*.jsonl')):
    with source.open('rb') as stream:
        first = stream.readline()
        try:
            metadata = json.loads(first).get('payload', {})
        except (ValueError, AttributeError):
            continue
        if metadata.get('cwd') != str(workspace):
            continue
        destination = target / source.name
        digest = hashlib.sha256()
        first_time = last_time = None
        records = byte_count = 0
        tools = set()
        with destination.open('xb') as output:
            stream.seek(0)
            for line in stream:
                if not line.endswith(b'\n'):
                    break  # Active writer may not yet have finished this record.
                record = json.loads(line)
                output.write(line)
                digest.update(line)
                records += 1
                byte_count += len(line)
                timestamp = record.get('timestamp')
                if timestamp:
                    first_time = first_time or timestamp
                    last_time = timestamp
                payload = record.get('payload', {})
                if isinstance(payload, dict) and payload.get('type') in ('function_call', 'custom_tool_call'):
                    tools.add(payload.get('name', 'unknown'))
        entries.append({'product': 'Codex', 'nativeFormat': 'JSONL rollout, complete records copied byte for byte',
                        'nativeSourcePath': str(source), 'archivePath': str(destination.relative_to(archive)),
                        'sessionId': metadata.get('id'), 'firstTimestamp': first_time, 'lastTimestamp': last_time,
                        'records': records, 'bytes': byte_count, 'sha256': digest.hexdigest(), 'tools': sorted(tools)})
if completed_turn and not any(entry['nativeSourcePath'] == completed_turn['nativeSourcePath']
                              and entry['lastTimestamp'] >= completed_turn['timestamp'] for entry in entries):
    raise SystemExit('The completed delivery turn was not included in the copied native records.')
manifest = {'workspace': str(workspace), 'exportedAt': datetime.now(timezone.utc).isoformat(),
            'scope': 'All locally accessible native Codex sessions whose initial workspace exactly matches this project, including matching agent sessions.',
            'completeThroughProjectEnd': completed_turn is not None,
            'completionBoundary': completed_turn,
            'remainingDependency': ('None for the locally accessible project sessions through the recorded delivery turn. Future turns require a new export; other products or inaccessible sessions are outside this verified scope.' if completed_turn else 'Run this script after the final response, or use --after-current-turn before it. Any other tool/product or unavailable historical session requires its own native full export; no summary substitutes.'),
            'containsPrivateConversationData': True, 'files': entries}
for path in (target / 'manifest.json', archive / 'manifest.json'):
    path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + '\n')
    path.chmod(0o600)
print(json.dumps({'nativeSessions': len(entries), 'archive': str(target), 'manifest': str(archive / 'manifest.json'), 'completeThroughProjectEnd': completed_turn is not None}))
