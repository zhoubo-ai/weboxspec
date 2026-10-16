#!/usr/bin/env python3
"""Wait for the delivery reply, export native conversations, then record only that verified completion."""
from pathlib import Path
import hashlib
import json
import re
import subprocess
import sys

root = Path(__file__).resolve().parents[1]
subprocess.run([sys.executable, str(root / 'scripts/export-conversations.py'), '--after-current-turn'], check=True)
manifest = json.loads((root / 'ai-conversations/manifest.json').read_text())
if not manifest['completeThroughProjectEnd'] or not manifest['files']:
    raise SystemExit('No complete native delivery-turn export was produced; acceptance unchanged.')
for item in manifest['files']:
    archive = root / 'ai-conversations' / item['archivePath']
    if archive.stat().st_size != item['bytes'] or hashlib.sha256(archive.read_bytes()).hexdigest() != item['sha256']:
        raise SystemExit('Archive verification failed; acceptance unchanged.')
feature = root / 'specs/001-webox-meal-ordering'
acceptance = feature / 'acceptance.md'
lines = []
for line in acceptance.read_text().splitlines():
    if line.startswith('| <a id="ac-dc-010">'):
        row = line.split('|')
        row[-3] = ' 通过 '
        row[-2] = ' [原生完整会话及最终回复，SHA-256已核验](../../ai-conversations/manifest.json) '
        line = '|'.join(row)
    lines.append(line)
text = '\n'.join(lines) + '\n'
passed = sum(line.split('|')[-3].strip() == '通过' for line in lines if line.startswith('| <a id="ac-'))
text = re.sub(r'\d+项通过、\d+项未执行', f'{passed}项通过、{81-passed}项未执行', text)
acceptance.write_text(text)
tasks = feature / 'tasks.md'
tasks.write_text(tasks.read_text().replace('- [ ] T136', '- [X] T136'))
for path in (feature / 'tasks.md', feature / 'plan.md', feature / 'quickstart.md'):
    text = path.read_text()
    text = re.sub(r'\d+项AC通过、\d+项未执行', f'{passed}项AC通过、{81-passed}项未执行', text)
    text = re.sub(r'\d+项通过、\d+项未执行', f'{passed}项通过、{81-passed}项未执行', text)
    text = text.replace('T136将在本轮结束的原生导出核验后更新', 'T136原生对话已完整导出至本轮结束并核验')
    text = text.replace('最终对话由本轮结束导出流程补齐', '本轮原生对话完整导出已核验')
    text = text.replace('剩余真人任务及本轮最终原生导出分别记录', '剩余项目均为真人验收，原生对话已导出并核验')
    path.write_text(text)
readme = root / 'README.md'
readme.write_text(readme.read_text().replace('原生对话将在本轮结束后自动补齐并核验', '原生对话已完整导出至本次交付回复并核验'))
subprocess.run([sys.executable, str(root / 'scripts/update-traceability.py')], check=True)
print(json.dumps({'nativeSessions': len(manifest['files']), 'completeThroughDeliveryTurn': True,
                  'acceptancePassed': passed, 'acceptancePending': 81-passed}))
