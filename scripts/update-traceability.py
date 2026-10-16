#!/usr/bin/env python3
"""Rebuild the evidence index from maintained acceptance states; never mark an AC or task passed."""
from pathlib import Path
import os
import re

root = Path(__file__).resolve().parents[1]
feature = root / 'specs/001-webox-meal-ordering'
acceptance = (feature / 'acceptance.md').read_text()
tasks = (feature / 'tasks.md').read_text()
source = (feature / 'requirements-source.md').read_text()
decisions = (feature / 'decisions.md').read_text()
prefix = '../../specs/001-webox-meal-ordering/'

def cells(line):
    return [part.strip() for part in line.strip().strip('|').split('|')]

def link(identifier, document='acceptance.md'):
    return f'[{identifier}]({prefix}{document}#{identifier.lower()})'

def relocate(text):
    return re.sub(r'\((spec|acceptance|decisions|delivery-constraints)\.md', lambda m: '(' + prefix + m[1] + '.md', text)

def relocate_evidence(text):
    def replace(match):
        destination = match[1]
        if re.match(r'^[a-z]+:', destination):
            return match[0]
        filename, separator, fragment = destination.partition('#')
        target = (feature / filename).resolve()
        return '](' + os.path.relpath(target, root / 'docs/validation') + (separator + fragment) + ')'
    return re.sub(r'\]\(([^)]+)\)', replace, text)

ac = {}
for line in acceptance.splitlines():
    if line.startswith('| <a id="ac-'):
        row = cells(line)
        identifier = re.search(r'AC-[A-Z0-9-]+', row[0])[0]
        ac[identifier] = {'status': row[-2], 'evidence': row[-1], 'row': row}
assert len(ac) == 81
fr_tasks = {re.search(r'FR-\d{3}', line)[0]: cells(line)[1:] for line in tasks.splitlines() if line.startswith('| [FR-')}
ac_tasks = {re.search(r'AC-[A-Z0-9-]+', line)[0]: cells(line)[1] for line in tasks.splitlines() if line.startswith('| [AC-')}
assert len(fr_tasks) == 47 and len(ac_tasks) == 81
passed = sum(value['status'] == '通过' for value in ac.values())
text = [f'# 实现与验收追溯\n\n本索引从维护的验收台账生成，当前81项AC中{passed}项通过、{81-passed}项尚未通过。索引和链接存在不构成新的运行证据；它不会自动改变AC或任务状态。完整运行与历史失败见[release.md](release.md)。\n',
        '原始PRD SHA-256为`21230fe66d756426d2c808e4cb1e3b80551035c9464c218261cc35caf45de8a5`。118条REQ包含产品、交付和岗位评分，不能当作118个功能，也不据此宣称岗位等级。\n',
        '## 47条功能要求\n\n| 要求 | 实现任务 | 验证任务 | 场景 | 当前状态 |\n| --- | --- | --- | --- | --- |']
updated = []
for line in acceptance.splitlines():
    if line.startswith('| [FR-'):
        row = cells(line);identifier = re.search(r'FR-\d{3}', row[0])[0]
        ids = list(dict.fromkeys(re.findall(r'\[?(AC-[A-Z0-9-]+)', row[1])))
        row[-1] = '通过' if all(ac[value]['status'] == '通过' for value in ids) else '未执行'
        line = '| ' + ' | '.join(row) + ' |'
        implementation, validation = fr_tasks[identifier]
        text.append(f'| {link(identifier,"spec.md")} | {implementation} | {validation} | {relocate(row[1])} | {row[-1]} |')
    updated.append(line)
(feature / 'acceptance.md').write_text('\n'.join(updated) + '\n')
text.append('\n## 11条质量目标与10条交付约束\n\n| 要求 | AC | 任务 | 状态 | 证据 |\n| --- | --- | --- | --- | --- |')
for identifier, value in ac.items():
    if identifier.startswith(('AC-SC-', 'AC-DC-')):
        requirement = identifier[3:];document = 'spec.md' if requirement.startswith('SC-') else 'delivery-constraints.md'
        text.append(f'| {link(requirement,document)} | {link(identifier)} | {ac_tasks[identifier]} | {value["status"]} | {relocate_evidence(value["evidence"])} |')
text.append('\n## 10项修订决策\n\n| 决策 | 对应验收 | 执行状态 |\n| --- | --- | --- |')
sections = re.split(r'(?=## DEC-R\d{2})', decisions)
for section in sections:
    if not section.startswith('## DEC-R'):
        continue
    identifier = re.search(r'DEC-R\d{2}', section)[0]
    ids = list(dict.fromkeys(re.findall(r'\[(AC-[A-Z0-9-]+)\]', section)))
    assert ids and all(value in ac for value in ids)
    state = '关联AC通过' if all(ac[value]['status'] == '通过' for value in ids) else '关联AC仍有待办'
    text.append(f'| {link(identifier,"decisions.md")} | {"、".join(link(value) for value in ids)} | {state} |')
text.append('\n## 81项验收与任务归属\n\n| AC | 任务 | 实际状态 | 证据 |\n| --- | --- | --- | --- |')
for identifier, value in ac.items():
    text.append(f'| {link(identifier)} | {ac_tasks[identifier]} | {value["status"]} | {relocate_evidence(value["evidence"])} |')
text.append('\n## 118条来源入口\n\n下列状态只描述来源表列出的代表性AC；完整要求仍须同时查看上面的FR/SC/DC与全部验收。原文性质、逐字短引、行号及参考文件哈希保留在[来源台账](' + prefix + 'requirements-source.md)。\n\n| 来源 | 当前要求 | 代表性AC | 关联执行状态 |\n| --- | --- | --- | --- |')
count = 0
for line in source.splitlines():
    if not line.startswith('| <a id="req-'):
        continue
    row = cells(line);identifier = re.search(r'REQ-\d{3}', row[0])[0]
    ids = list(dict.fromkeys(re.findall(r'\[(AC-[A-Z0-9-]+)\]', row[5])))
    assert ids and all(value in ac for value in ids)
    state = '关联AC通过' if all(ac[value]['status'] == '通过' for value in ids) else '关联AC仍有待办'
    text.append(f'| {link(identifier,"requirements-source.md")} | {relocate(row[4])} | {relocate(row[5])} | {state} |');count += 1
assert count == 118
(root / 'docs/validation/traceability.md').write_text('\n'.join(text) + '\n')
print(f'Indexed 47 FR, 11 SC, 10 DC, 10 decisions, 81 AC and {count} source requirements; AC states unchanged.')
