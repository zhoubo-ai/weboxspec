# 实现与验收追溯

本索引从维护的验收台账生成，当前81项AC中77项通过、4项尚未通过。索引和链接存在不构成新的运行证据；它不会自动改变AC或任务状态。完整运行与历史失败见[release.md](release.md)。

原始PRD SHA-256为`21230fe66d756426d2c808e4cb1e3b80551035c9464c218261cc35caf45de8a5`。118条REQ包含产品、交付和岗位评分，不能当作118个功能，也不据此宣称岗位等级。

## 47条功能要求

| 要求 | 实现任务 | 验证任务 | 场景 | 当前状态 |
| --- | --- | --- | --- | --- |
| [FR-001](../../specs/001-webox-meal-ordering/spec.md#fr-001) | T011、T016、T017 | T126、T116 | [AC-SC-002](../../specs/001-webox-meal-ordering/acceptance.md#ac-sc-002) | 通过 |
| [FR-002](../../specs/001-webox-meal-ordering/spec.md#fr-002) | T017、T041、T060、T115 | T126 | [AC-SC-002](../../specs/001-webox-meal-ordering/acceptance.md#ac-sc-002) | 通过 |
| [FR-003](../../specs/001-webox-meal-ordering/spec.md#fr-003) | T009、T039、T053、T056 | T030、T062 | [AC-US02-06](../../specs/001-webox-meal-ordering/acceptance.md#ac-us02-06) | 通过 |
| [FR-004](../../specs/001-webox-meal-ordering/spec.md#fr-004) | T009、T011、T023、T036、T060 | T125、T029、T045 | [AC-US01-05](../../specs/001-webox-meal-ordering/acceptance.md#ac-us01-05)、[AC-US03-06](../../specs/001-webox-meal-ordering/acceptance.md#ac-us03-06) | 通过 |
| [FR-005](../../specs/001-webox-meal-ordering/spec.md#fr-005) | T022、T023、T024 | T020、T028 | [AC-US01-01](../../specs/001-webox-meal-ordering/acceptance.md#ac-us01-01)、[AC-US01-02](../../specs/001-webox-meal-ordering/acceptance.md#ac-us01-02) | 通过 |
| [FR-006](../../specs/001-webox-meal-ordering/spec.md#fr-006) | T022、T023 | T020、T125 | [AC-US01-01](../../specs/001-webox-meal-ordering/acceptance.md#ac-us01-01)、[AC-US01-02](../../specs/001-webox-meal-ordering/acceptance.md#ac-us01-02)、[AC-US01-05](../../specs/001-webox-meal-ordering/acceptance.md#ac-us01-05) | 通过 |
| [FR-007](../../specs/001-webox-meal-ordering/spec.md#fr-007) | T012、T014、T024、T027 | T028、T125、T130 | [AC-US01-02](../../specs/001-webox-meal-ordering/acceptance.md#ac-us01-02)、[AC-US01-03](../../specs/001-webox-meal-ordering/acceptance.md#ac-us01-03) | 通过 |
| [FR-008](../../specs/001-webox-meal-ordering/spec.md#fr-008) | T014、T027、T059、T073、T088 | T020、T045、T125 | [AC-US01-04](../../specs/001-webox-meal-ordering/acceptance.md#ac-us01-04) | 通过 |
| [FR-009](../../specs/001-webox-meal-ordering/spec.md#fr-009) | T004、T012、T048、T050、T072、T033、T035 | T130 | [AC-SC-006](../../specs/001-webox-meal-ordering/acceptance.md#ac-sc-006) | 通过 |
| [FR-010](../../specs/001-webox-meal-ordering/spec.md#fr-010) | T036、T037、T038、T041 | T029、T044 | [AC-US02-01](../../specs/001-webox-meal-ordering/acceptance.md#ac-us02-01) | 通过 |
| [FR-011](../../specs/001-webox-meal-ordering/spec.md#fr-011) | T034、T038、T042 | T029、T044 | [AC-US02-01](../../specs/001-webox-meal-ordering/acceptance.md#ac-us02-01) | 通过 |
| [FR-012](../../specs/001-webox-meal-ordering/spec.md#fr-012) | T036、T041 | T029、T044 | [AC-US02-02](../../specs/001-webox-meal-ordering/acceptance.md#ac-us02-02) | 通过 |
| [FR-013](../../specs/001-webox-meal-ordering/spec.md#fr-013) | T034、T039、T042、T053 | T030、T045 | [AC-US02-03](../../specs/001-webox-meal-ordering/acceptance.md#ac-us02-03) | 通过 |
| [FR-014](../../specs/001-webox-meal-ordering/spec.md#fr-014) | T039、T040 | T030、T031、T044 | [AC-US02-04](../../specs/001-webox-meal-ordering/acceptance.md#ac-us02-04)、[AC-US02-07](../../specs/001-webox-meal-ordering/acceptance.md#ac-us02-07) | 通过 |
| [FR-015](../../specs/001-webox-meal-ordering/spec.md#fr-015) | T039、T043 | T030、T132 | [AC-US02-05](../../specs/001-webox-meal-ordering/acceptance.md#ac-us02-05)、[AC-US02-06](../../specs/001-webox-meal-ordering/acceptance.md#ac-us02-06) | 通过 |
| [FR-016](../../specs/001-webox-meal-ordering/spec.md#fr-016) | T048、T053、T060 | T045、T062 | [AC-US03-01](../../specs/001-webox-meal-ordering/acceptance.md#ac-us03-01) | 通过 |
| [FR-017](../../specs/001-webox-meal-ordering/spec.md#fr-017) | T052、T053、T056、T060 | T046、T069 | [AC-US03-06](../../specs/001-webox-meal-ordering/acceptance.md#ac-us03-06)、[AC-US04-06](../../specs/001-webox-meal-ordering/acceptance.md#ac-us04-06) | 通过 |
| [FR-018](../../specs/001-webox-meal-ordering/spec.md#fr-018) | T054、T056、T057、T060 | T046、T047、T062 | [AC-US03-03](../../specs/001-webox-meal-ordering/acceptance.md#ac-us03-03)、[AC-US04-05](../../specs/001-webox-meal-ordering/acceptance.md#ac-us04-05) | 通过 |
| [FR-019](../../specs/001-webox-meal-ordering/spec.md#fr-019) | T055、T056、T060、T061 | T046、T062 | [AC-US03-02](../../specs/001-webox-meal-ordering/acceptance.md#ac-us03-02) | 通过 |
| [FR-020](../../specs/001-webox-meal-ordering/spec.md#fr-020) | T051、T059、T061 | T045、T062、T094 | [AC-US03-04](../../specs/001-webox-meal-ordering/acceptance.md#ac-us03-04) | 通过 |
| [FR-021](../../specs/001-webox-meal-ordering/spec.md#fr-021) | T058、T059、T061 | T046、T081、T062 | [AC-US03-05](../../specs/001-webox-meal-ordering/acceptance.md#ac-us03-05)、[AC-US06-07](../../specs/001-webox-meal-ordering/acceptance.md#ac-us06-07) | 通过 |
| [FR-022](../../specs/001-webox-meal-ordering/spec.md#fr-022) | T039、T052、T068 | T030、T063、T069 | [AC-US02-05](../../specs/001-webox-meal-ordering/acceptance.md#ac-us02-05)、[AC-US04-01](../../specs/001-webox-meal-ordering/acceptance.md#ac-us04-01) | 通过 |
| [FR-023](../../specs/001-webox-meal-ordering/spec.md#fr-023) | T052、T056、T065 | T046、T063、T069 | [AC-US04-02](../../specs/001-webox-meal-ordering/acceptance.md#ac-us04-02)、[AC-US04-03](../../specs/001-webox-meal-ordering/acceptance.md#ac-us04-03) | 通过 |
| [FR-024](../../specs/001-webox-meal-ordering/spec.md#fr-024) | T052、T066、T060 | T064、T069 | [AC-US04-02](../../specs/001-webox-meal-ordering/acceptance.md#ac-us04-02) | 通过 |
| [FR-025](../../specs/001-webox-meal-ordering/spec.md#fr-025) | T050、T056、T067 | T046、T063、T069 | [AC-US04-04](../../specs/001-webox-meal-ordering/acceptance.md#ac-us04-04)、[AC-US04-05](../../specs/001-webox-meal-ordering/acceptance.md#ac-us04-05) | 通过 |
| [FR-026](../../specs/001-webox-meal-ordering/spec.md#fr-026) | T040、T053、T066、T067 | T031、T064、T069 | [AC-US02-07](../../specs/001-webox-meal-ordering/acceptance.md#ac-us02-07)、[AC-US04-03](../../specs/001-webox-meal-ordering/acceptance.md#ac-us04-03)、[AC-US04-06](../../specs/001-webox-meal-ordering/acceptance.md#ac-us04-06) | 通过 |
| [FR-027](../../specs/001-webox-meal-ordering/spec.md#fr-027) | T072、T073、T076 | T070、T079、T130 | [AC-US05-01](../../specs/001-webox-meal-ordering/acceptance.md#ac-us05-01) | 通过 |
| [FR-028](../../specs/001-webox-meal-ordering/spec.md#fr-028) | T075、T077 | T070、T071、T079 | [AC-US05-02](../../specs/001-webox-meal-ordering/acceptance.md#ac-us05-02)、[AC-US05-05](../../specs/001-webox-meal-ordering/acceptance.md#ac-us05-05) | 通过 |
| [FR-029](../../specs/001-webox-meal-ordering/spec.md#fr-029) | T074、T076 | T071、T079 | [AC-US05-03](../../specs/001-webox-meal-ordering/acceptance.md#ac-us05-03) | 通过 |
| [FR-030](../../specs/001-webox-meal-ordering/spec.md#fr-030) | T075、T078 | T071、T079 | [AC-US05-04](../../specs/001-webox-meal-ordering/acceptance.md#ac-us05-04) | 通过 |
| [FR-031](../../specs/001-webox-meal-ordering/spec.md#fr-031) | T027、T091 | T080、T094 | [AC-US01-04](../../specs/001-webox-meal-ordering/acceptance.md#ac-us01-04)、[AC-US06-01](../../specs/001-webox-meal-ordering/acceptance.md#ac-us06-01) | 通过 |
| [FR-032](../../specs/001-webox-meal-ordering/spec.md#fr-032) | T034、T084、T085、T091 | T080、T082、T094 | [AC-US06-01](../../specs/001-webox-meal-ordering/acceptance.md#ac-us06-01)、[AC-US06-02](../../specs/001-webox-meal-ordering/acceptance.md#ac-us06-02)、[AC-US06-09](../../specs/001-webox-meal-ordering/acceptance.md#ac-us06-09) | 通过 |
| [FR-033](../../specs/001-webox-meal-ordering/spec.md#fr-033) | T085、T086、T091 | T080、T032、T094 | [AC-US04-06](../../specs/001-webox-meal-ordering/acceptance.md#ac-us04-06)、[AC-US06-03](../../specs/001-webox-meal-ordering/acceptance.md#ac-us06-03) | 通过 |
| [FR-034](../../specs/001-webox-meal-ordering/spec.md#fr-034) | T035、T087、T092 | T080、T094 | [AC-US06-04](../../specs/001-webox-meal-ordering/acceptance.md#ac-us06-04) | 通过 |
| [FR-035](../../specs/001-webox-meal-ordering/spec.md#fr-035) | T025、T089、T090 | T083、T094、T130 | [AC-US06-05](../../specs/001-webox-meal-ordering/acceptance.md#ac-us06-05)、[AC-US06-08](../../specs/001-webox-meal-ordering/acceptance.md#ac-us06-08) | 通过 |
| [FR-036](../../specs/001-webox-meal-ordering/spec.md#fr-036) | T058、T088、T093 | T081、T094 | [AC-US06-06](../../specs/001-webox-meal-ordering/acceptance.md#ac-us06-06)、[AC-US06-07](../../specs/001-webox-meal-ordering/acceptance.md#ac-us06-07) | 通过 |
| [FR-037](../../specs/001-webox-meal-ordering/spec.md#fr-037) | T035、T041、T100、T102 | T096、T103 | [AC-US07-01](../../specs/001-webox-meal-ordering/acceptance.md#ac-us07-01) | 通过 |
| [FR-038](../../specs/001-webox-meal-ordering/spec.md#fr-038) | T055、T056 | T046、T103 | [AC-US07-02](../../specs/001-webox-meal-ordering/acceptance.md#ac-us07-02)、[AC-US07-03](../../specs/001-webox-meal-ordering/acceptance.md#ac-us07-03) | 通过 |
| [FR-039](../../specs/001-webox-meal-ordering/spec.md#fr-039) | T013、T086、T097、T098、T099、T101 | T095、T103、T132 | [AC-US07-04](../../specs/001-webox-meal-ordering/acceptance.md#ac-us07-04) | 通过 |
| [FR-040](../../specs/001-webox-meal-ordering/spec.md#fr-040) | T055、T058、T087、T088 | T046、T081、T103 | [AC-US06-07](../../specs/001-webox-meal-ordering/acceptance.md#ac-us06-07)、[AC-US07-04](../../specs/001-webox-meal-ordering/acceptance.md#ac-us07-04) | 通过 |
| [FR-041](../../specs/001-webox-meal-ordering/spec.md#fr-041) | T110、T111、T109、T113 | T105、T116、T117 | [AC-US08-01](../../specs/001-webox-meal-ordering/acceptance.md#ac-us08-01)、[AC-US08-02](../../specs/001-webox-meal-ordering/acceptance.md#ac-us08-02) | 通过 |
| [FR-042](../../specs/001-webox-meal-ordering/spec.md#fr-042) | T111、T112 | T105、T116、T117 | [AC-US08-01](../../specs/001-webox-meal-ordering/acceptance.md#ac-us08-01)、[AC-US08-02](../../specs/001-webox-meal-ordering/acceptance.md#ac-us08-02)、[AC-US08-05](../../specs/001-webox-meal-ordering/acceptance.md#ac-us08-05) | 通过 |
| [FR-043](../../specs/001-webox-meal-ordering/spec.md#fr-043) | T109、T112、T114、T115 | T104、T107、T116、T117 | [AC-US08-03](../../specs/001-webox-meal-ordering/acceptance.md#ac-us08-03) | 通过 |
| [FR-044](../../specs/001-webox-meal-ordering/spec.md#fr-044) | T109、T113、T114、T115 | T106、T104、T107、T117 | [AC-US08-04](../../specs/001-webox-meal-ordering/acceptance.md#ac-us08-04) | 通过 |
| [FR-045](../../specs/001-webox-meal-ordering/spec.md#fr-045) | T121、T122、T123 | T118、T119、T124 | [AC-US09-01](../../specs/001-webox-meal-ordering/acceptance.md#ac-us09-01)、[AC-US09-02](../../specs/001-webox-meal-ordering/acceptance.md#ac-us09-02) | 通过 |
| [FR-046](../../specs/001-webox-meal-ordering/spec.md#fr-046) | T121、T122、T123 | T120、T124 | [AC-US09-03](../../specs/001-webox-meal-ordering/acceptance.md#ac-us09-03) | 通过 |
| [FR-047](../../specs/001-webox-meal-ordering/spec.md#fr-047) | T121、T122、T123 | T119、T124 | [AC-US09-01](../../specs/001-webox-meal-ordering/acceptance.md#ac-us09-01)、[AC-US09-02](../../specs/001-webox-meal-ordering/acceptance.md#ac-us09-02)、[AC-US09-04](../../specs/001-webox-meal-ordering/acceptance.md#ac-us09-04) | 通过 |

## 11条质量目标与10条交付约束

| 要求 | AC | 任务 | 状态 | 证据 |
| --- | --- | --- | --- | --- |
| [SC-001](../../specs/001-webox-meal-ordering/spec.md#sc-001) | [AC-SC-001](../../specs/001-webox-meal-ordering/acceptance.md#ac-sc-001) | T133 | 未执行 | [真人任务已准备，等待参与者](user-acceptance.md) |
| [SC-002](../../specs/001-webox-meal-ordering/spec.md#sc-002) | [AC-SC-002](../../specs/001-webox-meal-ordering/acceptance.md#ac-sc-002) | T126 | 通过 | [三浏览器/三尺寸/英文/键盘专项](accessibility-language.md) |
| [SC-003](../../specs/001-webox-meal-ordering/spec.md#sc-003) | [AC-SC-003](../../specs/001-webox-meal-ordering/acceptance.md#ac-sc-003) | T131、T132 | 通过 | [200会话600秒及140次DOM计时](performance.md) |
| [SC-004](../../specs/001-webox-meal-ordering/spec.md#sc-004) | [AC-SC-004](../../specs/001-webox-meal-ordering/acceptance.md#ac-sc-004) | T062 | 通过 | [US3验证](us3.md) |
| [SC-005](../../specs/001-webox-meal-ordering/spec.md#sc-005) | [AC-SC-005](../../specs/001-webox-meal-ordering/acceptance.md#ac-sc-005) | T069、T046 | 通过 | [US4验证](us4.md) |
| [SC-006](../../specs/001-webox-meal-ordering/spec.md#sc-006) | [AC-SC-006](../../specs/001-webox-meal-ordering/acceptance.md#ac-sc-006) | T125、T130 | 通过 | [应用/MySQL重启与隔离恢复](persistence.md)、[权限矩阵](security.md) |
| [SC-007](../../specs/001-webox-meal-ordering/spec.md#sc-007) | [AC-SC-007](../../specs/001-webox-meal-ordering/acceptance.md#ac-sc-007) | T094、T133 | 未执行 | [真人任务已准备，等待参与者](user-acceptance.md) |
| [SC-008](../../specs/001-webox-meal-ordering/spec.md#sc-008) | [AC-SC-008](../../specs/001-webox-meal-ordering/acceptance.md#ac-sc-008) | T079、T133 | 未执行 | [真人任务已准备，等待参与者](user-acceptance.md) |
| [SC-009](../../specs/001-webox-meal-ordering/spec.md#sc-009) | [AC-SC-009](../../specs/001-webox-meal-ordering/acceptance.md#ac-sc-009) | T103、T132 | 通过 | [20人争10份及取消](us7.md)、[200会话下库存计时](performance.md) |
| [SC-010](../../specs/001-webox-meal-ordering/spec.md#sc-010) | [AC-SC-010](../../specs/001-webox-meal-ordering/acceptance.md#ac-sc-010) | T116、T117、T132、T133 | 未执行 | [技术指标通过，真人相关性待验收](us8.md) |
| [SC-011](../../specs/001-webox-meal-ordering/spec.md#sc-011) | [AC-SC-011](../../specs/001-webox-meal-ordering/acceptance.md#ac-sc-011) | T124、T132 | 通过 | [US9验证](us9.md) |
| [DC-001](../../specs/001-webox-meal-ordering/delivery-constraints.md#dc-001) | [AC-DC-001](../../specs/001-webox-meal-ordering/acceptance.md#ac-dc-001) | T134、T137 | 通过 | [完整回归、真实模型与正式负载](release.md) |
| [DC-002](../../specs/001-webox-meal-ordering/delivery-constraints.md#dc-002) | [AC-DC-002](../../specs/001-webox-meal-ordering/acceptance.md#ac-dc-002) | T007、T134、T135 | 通过 | [JDK17与选型、实际构建](release.md)、[架构](../architecture.md) |
| [DC-003](../../specs/001-webox-meal-ordering/delivery-constraints.md#dc-003) | [AC-DC-003](../../specs/001-webox-meal-ordering/acceptance.md#ac-dc-003) | T004、T019、T130、T134 | 通过 | [独立MySQL重启及备份恢复](persistence.md) |
| [DC-004](../../specs/001-webox-meal-ordering/delivery-constraints.md#dc-004) | [AC-DC-004](../../specs/001-webox-meal-ordering/acceptance.md#ac-dc-004) | T032、T086、T094 | 通过 | [缓存命中/过期/容量](us2.md)、[版本失效/运营更新](us6.md) |
| [DC-005](../../specs/001-webox-meal-ordering/delivery-constraints.md#dc-005) | [AC-DC-005](../../specs/001-webox-meal-ordering/acceptance.md#ac-dc-005) | T126、T116 | 通过 | [界面英文与原文地址](accessibility-language.md)、[真实英文推荐](us8.md) |
| [DC-006](../../specs/001-webox-meal-ordering/delivery-constraints.md#dc-006) | [AC-DC-006](../../specs/001-webox-meal-ordering/acceptance.md#ac-dc-006) | T125、T127、T128、T129、T130 | 通过 | [生产HTTPS/限流/指标/维护](operations.md)、[安全](security.md)、[备份恢复](persistence.md) |
| [DC-007](../../specs/001-webox-meal-ordering/delivery-constraints.md#dc-007) | [AC-DC-007](../../specs/001-webox-meal-ordering/acceptance.md#ac-dc-007) | T134、T135 | 通过 | [从零启动README](../../README.md)、[隔离从零启动演练](persistence.md) |
| [DC-008](../../specs/001-webox-meal-ordering/delivery-constraints.md#dc-008) | [AC-DC-008](../../specs/001-webox-meal-ordering/acceptance.md#ac-dc-008) | T134、T135、T137 | 通过 | [API与流文档](../../specs/001-webox-meal-ordering/contracts/README.md)、[实现架构](../architecture.md)、[静态核验](release.md) |
| [DC-009](../../specs/001-webox-meal-ordering/delivery-constraints.md#dc-009) | [AC-DC-009](../../specs/001-webox-meal-ordering/acceptance.md#ac-dc-009) | T134、T137 | 通过 | [完整回归、真实模型与正式负载](release.md) |
| [DC-010](../../specs/001-webox-meal-ordering/delivery-constraints.md#dc-010) | [AC-DC-010](../../specs/001-webox-meal-ordering/acceptance.md#ac-dc-010) | T136 | 通过 | [原生完整会话及最终回复，SHA-256已核验](../../ai-conversations/manifest.json) |

## 10项修订决策

| 决策 | 对应验收 | 执行状态 |
| --- | --- | --- |
| [DEC-R01](../../specs/001-webox-meal-ordering/decisions.md#dec-r01) | [AC-US09-04](../../specs/001-webox-meal-ordering/acceptance.md#ac-us09-04) | 关联AC通过 |
| [DEC-R02](../../specs/001-webox-meal-ordering/decisions.md#dec-r02) | [AC-US06-07](../../specs/001-webox-meal-ordering/acceptance.md#ac-us06-07) | 关联AC通过 |
| [DEC-R03](../../specs/001-webox-meal-ordering/decisions.md#dec-r03) | [AC-US02-07](../../specs/001-webox-meal-ordering/acceptance.md#ac-us02-07) | 关联AC通过 |
| [DEC-R04](../../specs/001-webox-meal-ordering/decisions.md#dec-r04) | [AC-US06-09](../../specs/001-webox-meal-ordering/acceptance.md#ac-us06-09) | 关联AC通过 |
| [DEC-R05](../../specs/001-webox-meal-ordering/decisions.md#dec-r05) | [AC-US05-05](../../specs/001-webox-meal-ordering/acceptance.md#ac-us05-05) | 关联AC通过 |
| [DEC-R06](../../specs/001-webox-meal-ordering/decisions.md#dec-r06) | [AC-REV-09](../../specs/001-webox-meal-ordering/acceptance.md#ac-rev-09) | 关联AC通过 |
| [DEC-R07](../../specs/001-webox-meal-ordering/decisions.md#dec-r07) | [AC-REV-06](../../specs/001-webox-meal-ordering/acceptance.md#ac-rev-06) | 关联AC通过 |
| [DEC-R08](../../specs/001-webox-meal-ordering/decisions.md#dec-r08) | [AC-US06-08](../../specs/001-webox-meal-ordering/acceptance.md#ac-us06-08) | 关联AC通过 |
| [DEC-R09](../../specs/001-webox-meal-ordering/decisions.md#dec-r09) | [AC-SC-003](../../specs/001-webox-meal-ordering/acceptance.md#ac-sc-003) | 关联AC通过 |
| [DEC-R10](../../specs/001-webox-meal-ordering/decisions.md#dec-r10) | [AC-DC-008](../../specs/001-webox-meal-ordering/acceptance.md#ac-dc-008) | 关联AC通过 |

## 81项验收与任务归属

| AC | 任务 | 实际状态 | 证据 |
| --- | --- | --- | --- |
| [AC-US01-01](../../specs/001-webox-meal-ordering/acceptance.md#ac-us01-01) | T028、T125 | 通过 | [2026-10-16 US1实测](us1.md) |
| [AC-US01-02](../../specs/001-webox-meal-ordering/acceptance.md#ac-us01-02) | T028、T125 | 通过 | [2026-10-16 US1实测](us1.md) |
| [AC-US01-03](../../specs/001-webox-meal-ordering/acceptance.md#ac-us01-03) | T028、T125 | 通过 | [2026-10-16 US1实测](us1.md) |
| [AC-US01-04](../../specs/001-webox-meal-ordering/acceptance.md#ac-us01-04) | T028、T125 | 通过 | [US6验证](us6.md) |
| [AC-US01-05](../../specs/001-webox-meal-ordering/acceptance.md#ac-us01-05) | T028、T125 | 通过 | [2026-10-16 US1实测](us1.md) |
| [AC-US02-01](../../specs/001-webox-meal-ordering/acceptance.md#ac-us02-01) | T044 | 通过 | [US2验证](us2.md) |
| [AC-US02-02](../../specs/001-webox-meal-ordering/acceptance.md#ac-us02-02) | T044 | 通过 | [US2验证](us2.md) |
| [AC-US02-03](../../specs/001-webox-meal-ordering/acceptance.md#ac-us02-03) | T044 | 通过 | [US2验证](us2.md) |
| [AC-US02-04](../../specs/001-webox-meal-ordering/acceptance.md#ac-us02-04) | T044 | 通过 | [US2验证](us2.md) |
| [AC-US02-05](../../specs/001-webox-meal-ordering/acceptance.md#ac-us02-05) | T044 | 通过 | [US2验证](us2.md) |
| [AC-US02-06](../../specs/001-webox-meal-ordering/acceptance.md#ac-us02-06) | T044 | 通过 | [US2验证](us2.md) |
| [AC-US02-07](../../specs/001-webox-meal-ordering/acceptance.md#ac-us02-07) | T044 | 通过 | [US2验证](us2.md) |
| [AC-US03-01](../../specs/001-webox-meal-ordering/acceptance.md#ac-us03-01) | T062 | 通过 | [US3验证](us3.md) |
| [AC-US03-02](../../specs/001-webox-meal-ordering/acceptance.md#ac-us03-02) | T062 | 通过 | [US3验证](us3.md) |
| [AC-US03-03](../../specs/001-webox-meal-ordering/acceptance.md#ac-us03-03) | T062 | 通过 | [US3验证](us3.md) |
| [AC-US03-04](../../specs/001-webox-meal-ordering/acceptance.md#ac-us03-04) | T062 | 通过 | [US3验证](us3.md) |
| [AC-US03-05](../../specs/001-webox-meal-ordering/acceptance.md#ac-us03-05) | T062 | 通过 | [US3验证](us3.md) |
| [AC-US03-06](../../specs/001-webox-meal-ordering/acceptance.md#ac-us03-06) | T062 | 通过 | [US3验证](us3.md) |
| [AC-US04-01](../../specs/001-webox-meal-ordering/acceptance.md#ac-us04-01) | T069 | 通过 | [US4验证](us4.md) |
| [AC-US04-02](../../specs/001-webox-meal-ordering/acceptance.md#ac-us04-02) | T069 | 通过 | [US4验证](us4.md) |
| [AC-US04-03](../../specs/001-webox-meal-ordering/acceptance.md#ac-us04-03) | T069 | 通过 | [US4验证](us4.md) |
| [AC-US04-04](../../specs/001-webox-meal-ordering/acceptance.md#ac-us04-04) | T069 | 通过 | [US4验证](us4.md) |
| [AC-US04-05](../../specs/001-webox-meal-ordering/acceptance.md#ac-us04-05) | T069 | 通过 | [US4验证](us4.md) |
| [AC-US04-06](../../specs/001-webox-meal-ordering/acceptance.md#ac-us04-06) | T069 | 通过 | [US4验证](us4.md) |
| [AC-US05-01](../../specs/001-webox-meal-ordering/acceptance.md#ac-us05-01) | T079 | 通过 | [US5验证](us5.md) |
| [AC-US05-02](../../specs/001-webox-meal-ordering/acceptance.md#ac-us05-02) | T079 | 通过 | [US5验证](us5.md) |
| [AC-US05-03](../../specs/001-webox-meal-ordering/acceptance.md#ac-us05-03) | T079 | 通过 | [US5验证](us5.md) |
| [AC-US05-04](../../specs/001-webox-meal-ordering/acceptance.md#ac-us05-04) | T079 | 通过 | [US5验证](us5.md) |
| [AC-US05-05](../../specs/001-webox-meal-ordering/acceptance.md#ac-us05-05) | T079 | 通过 | [US5验证](us5.md) |
| [AC-US06-01](../../specs/001-webox-meal-ordering/acceptance.md#ac-us06-01) | T094 | 通过 | [US6验证](us6.md) |
| [AC-US06-02](../../specs/001-webox-meal-ordering/acceptance.md#ac-us06-02) | T094 | 通过 | [US6验证](us6.md) |
| [AC-US06-03](../../specs/001-webox-meal-ordering/acceptance.md#ac-us06-03) | T094 | 通过 | [US6验证](us6.md) |
| [AC-US06-04](../../specs/001-webox-meal-ordering/acceptance.md#ac-us06-04) | T094 | 通过 | [US6验证](us6.md) |
| [AC-US06-05](../../specs/001-webox-meal-ordering/acceptance.md#ac-us06-05) | T094 | 通过 | [US6验证](us6.md) |
| [AC-US06-06](../../specs/001-webox-meal-ordering/acceptance.md#ac-us06-06) | T094 | 通过 | [US6验证](us6.md) |
| [AC-US06-07](../../specs/001-webox-meal-ordering/acceptance.md#ac-us06-07) | T094 | 通过 | [US6验证](us6.md) |
| [AC-US06-08](../../specs/001-webox-meal-ordering/acceptance.md#ac-us06-08) | T094 | 通过 | [US6验证](us6.md) |
| [AC-US06-09](../../specs/001-webox-meal-ordering/acceptance.md#ac-us06-09) | T094 | 通过 | [US6验证](us6.md) |
| [AC-US07-01](../../specs/001-webox-meal-ordering/acceptance.md#ac-us07-01) | T103、T132 | 通过 | [US7验证](us7.md) |
| [AC-US07-02](../../specs/001-webox-meal-ordering/acceptance.md#ac-us07-02) | T103、T132 | 通过 | [US7验证](us7.md) |
| [AC-US07-03](../../specs/001-webox-meal-ordering/acceptance.md#ac-us07-03) | T103、T132 | 通过 | [US7验证](us7.md) |
| [AC-US07-04](../../specs/001-webox-meal-ordering/acceptance.md#ac-us07-04) | T103、T132 | 通过 | [US7验证](us7.md) |
| [AC-US08-01](../../specs/001-webox-meal-ordering/acceptance.md#ac-us08-01) | T116、T117 | 通过 | [US8验证](us8.md) |
| [AC-US08-02](../../specs/001-webox-meal-ordering/acceptance.md#ac-us08-02) | T116、T117 | 通过 | [US8验证](us8.md) |
| [AC-US08-03](../../specs/001-webox-meal-ordering/acceptance.md#ac-us08-03) | T116、T117 | 通过 | [US8验证](us8.md) |
| [AC-US08-04](../../specs/001-webox-meal-ordering/acceptance.md#ac-us08-04) | T116、T117 | 通过 | [US8验证](us8.md) |
| [AC-US08-05](../../specs/001-webox-meal-ordering/acceptance.md#ac-us08-05) | T116、T117 | 通过 | [US8验证](us8.md) |
| [AC-US09-01](../../specs/001-webox-meal-ordering/acceptance.md#ac-us09-01) | T124 | 通过 | [US9验证](us9.md) |
| [AC-US09-02](../../specs/001-webox-meal-ordering/acceptance.md#ac-us09-02) | T124 | 通过 | [US9验证](us9.md) |
| [AC-US09-03](../../specs/001-webox-meal-ordering/acceptance.md#ac-us09-03) | T124 | 通过 | [US9验证](us9.md) |
| [AC-US09-04](../../specs/001-webox-meal-ordering/acceptance.md#ac-us09-04) | T124 | 通过 | [US9验证](us9.md) |
| [AC-SC-001](../../specs/001-webox-meal-ordering/acceptance.md#ac-sc-001) | T133 | 未执行 | [真人任务已准备，等待参与者](user-acceptance.md) |
| [AC-SC-002](../../specs/001-webox-meal-ordering/acceptance.md#ac-sc-002) | T126 | 通过 | [三浏览器/三尺寸/英文/键盘专项](accessibility-language.md) |
| [AC-SC-003](../../specs/001-webox-meal-ordering/acceptance.md#ac-sc-003) | T131、T132 | 通过 | [200会话600秒及140次DOM计时](performance.md) |
| [AC-SC-004](../../specs/001-webox-meal-ordering/acceptance.md#ac-sc-004) | T062 | 通过 | [US3验证](us3.md) |
| [AC-SC-005](../../specs/001-webox-meal-ordering/acceptance.md#ac-sc-005) | T069、T046 | 通过 | [US4验证](us4.md) |
| [AC-SC-006](../../specs/001-webox-meal-ordering/acceptance.md#ac-sc-006) | T125、T130 | 通过 | [应用/MySQL重启与隔离恢复](persistence.md)、[权限矩阵](security.md) |
| [AC-SC-007](../../specs/001-webox-meal-ordering/acceptance.md#ac-sc-007) | T094、T133 | 未执行 | [真人任务已准备，等待参与者](user-acceptance.md) |
| [AC-SC-008](../../specs/001-webox-meal-ordering/acceptance.md#ac-sc-008) | T079、T133 | 未执行 | [真人任务已准备，等待参与者](user-acceptance.md) |
| [AC-SC-009](../../specs/001-webox-meal-ordering/acceptance.md#ac-sc-009) | T103、T132 | 通过 | [20人争10份及取消](us7.md)、[200会话下库存计时](performance.md) |
| [AC-SC-010](../../specs/001-webox-meal-ordering/acceptance.md#ac-sc-010) | T116、T117、T132、T133 | 未执行 | [技术指标通过，真人相关性待验收](us8.md) |
| [AC-SC-011](../../specs/001-webox-meal-ordering/acceptance.md#ac-sc-011) | T124、T132 | 通过 | [US9验证](us9.md) |
| [AC-DC-001](../../specs/001-webox-meal-ordering/acceptance.md#ac-dc-001) | T134、T137 | 通过 | [完整回归、真实模型与正式负载](release.md) |
| [AC-DC-002](../../specs/001-webox-meal-ordering/acceptance.md#ac-dc-002) | T007、T134、T135 | 通过 | [JDK17与选型、实际构建](release.md)、[架构](../architecture.md) |
| [AC-DC-003](../../specs/001-webox-meal-ordering/acceptance.md#ac-dc-003) | T004、T019、T130、T134 | 通过 | [独立MySQL重启及备份恢复](persistence.md) |
| [AC-DC-004](../../specs/001-webox-meal-ordering/acceptance.md#ac-dc-004) | T032、T086、T094 | 通过 | [缓存命中/过期/容量](us2.md)、[版本失效/运营更新](us6.md) |
| [AC-DC-005](../../specs/001-webox-meal-ordering/acceptance.md#ac-dc-005) | T126、T116 | 通过 | [界面英文与原文地址](accessibility-language.md)、[真实英文推荐](us8.md) |
| [AC-DC-006](../../specs/001-webox-meal-ordering/acceptance.md#ac-dc-006) | T125、T127、T128、T129、T130 | 通过 | [生产HTTPS/限流/指标/维护](operations.md)、[安全](security.md)、[备份恢复](persistence.md) |
| [AC-DC-007](../../specs/001-webox-meal-ordering/acceptance.md#ac-dc-007) | T134、T135 | 通过 | [从零启动README](../../README.md)、[隔离从零启动演练](persistence.md) |
| [AC-DC-008](../../specs/001-webox-meal-ordering/acceptance.md#ac-dc-008) | T134、T135、T137 | 通过 | [API与流文档](../../specs/001-webox-meal-ordering/contracts/README.md)、[实现架构](../architecture.md)、[静态核验](release.md) |
| [AC-DC-009](../../specs/001-webox-meal-ordering/acceptance.md#ac-dc-009) | T134、T137 | 通过 | [完整回归、真实模型与正式负载](release.md) |
| [AC-DC-010](../../specs/001-webox-meal-ordering/acceptance.md#ac-dc-010) | T136 | 通过 | [原生完整会话及最终回复，SHA-256已核验](../../ai-conversations/manifest.json) |
| [AC-REV-01](../../specs/001-webox-meal-ordering/acceptance.md#ac-rev-01) | T124 | 通过 | [US9验证](us9.md) |
| [AC-REV-02](../../specs/001-webox-meal-ordering/acceptance.md#ac-rev-02) | T031、T044 | 通过 | [US2验证](us2.md) |
| [AC-REV-03](../../specs/001-webox-meal-ordering/acceptance.md#ac-rev-03) | T079、T105、T117 | 通过 | [US8验证](us8.md) |
| [AC-REV-04](../../specs/001-webox-meal-ordering/acceptance.md#ac-rev-04) | T081、T094 | 通过 | [US6验证](us6.md) |
| [AC-REV-05](../../specs/001-webox-meal-ordering/acceptance.md#ac-rev-05) | T082、T094 | 通过 | [US6验证](us6.md) |
| [AC-REV-06](../../specs/001-webox-meal-ordering/acceptance.md#ac-rev-06) | T086、T094 | 通过 | [US6验证](us6.md) |
| [AC-REV-07](../../specs/001-webox-meal-ordering/acceptance.md#ac-rev-07) | T032、T044 | 通过 | [US2验证](us2.md) |
| [AC-REV-08](../../specs/001-webox-meal-ordering/acceptance.md#ac-rev-08) | T083、T094、T130 | 通过 | [US6验证](us6.md) |
| [AC-REV-09](../../specs/001-webox-meal-ordering/acceptance.md#ac-rev-09) | T062、T126 | 通过 | [US3验证](us3.md) |

## 118条来源入口

下列状态只描述来源表列出的代表性AC；完整要求仍须同时查看上面的FR/SC/DC与全部验收。原文性质、逐字短引、行号及参考文件哈希保留在[来源台账](../../specs/001-webox-meal-ordering/requirements-source.md)。

| 来源 | 当前要求 | 代表性AC | 关联执行状态 |
| --- | --- | --- | --- |
| [REQ-001](../../specs/001-webox-meal-ordering/requirements-source.md#req-001) | [FR-002](../../specs/001-webox-meal-ordering/spec.md#fr-002) | [AC-SC-002](../../specs/001-webox-meal-ordering/acceptance.md#ac-sc-002) | 关联AC通过 |
| [REQ-002](../../specs/001-webox-meal-ordering/requirements-source.md#req-002) | [FR-008](../../specs/001-webox-meal-ordering/spec.md#fr-008)、[FR-031](../../specs/001-webox-meal-ordering/spec.md#fr-031) | [AC-US01-04](../../specs/001-webox-meal-ordering/acceptance.md#ac-us01-04) | 关联AC通过 |
| [REQ-003](../../specs/001-webox-meal-ordering/requirements-source.md#req-003) | [FR-031](../../specs/001-webox-meal-ordering/spec.md#fr-031)、[FR-032](../../specs/001-webox-meal-ordering/spec.md#fr-032)、[FR-036](../../specs/001-webox-meal-ordering/spec.md#fr-036)、[FR-045](../../specs/001-webox-meal-ordering/spec.md#fr-045) | [AC-US01-04](../../specs/001-webox-meal-ordering/acceptance.md#ac-us01-04)、[AC-US06-01](../../specs/001-webox-meal-ordering/acceptance.md#ac-us06-01)、[AC-US06-06](../../specs/001-webox-meal-ordering/acceptance.md#ac-us06-06)、[AC-US09-01](../../specs/001-webox-meal-ordering/acceptance.md#ac-us09-01) | 关联AC通过 |
| [REQ-004](../../specs/001-webox-meal-ordering/requirements-source.md#req-004) | [FR-001](../../specs/001-webox-meal-ordering/spec.md#fr-001) | [AC-SC-002](../../specs/001-webox-meal-ordering/acceptance.md#ac-sc-002) | 关联AC通过 |
| [REQ-005](../../specs/001-webox-meal-ordering/requirements-source.md#req-005) | [DC-001](../../specs/001-webox-meal-ordering/delivery-constraints.md#dc-001) | [AC-DC-001](../../specs/001-webox-meal-ordering/acceptance.md#ac-dc-001) | 关联AC通过 |
| [REQ-006](../../specs/001-webox-meal-ordering/requirements-source.md#req-006) | [FR-005](../../specs/001-webox-meal-ordering/spec.md#fr-005) | [AC-US01-01](../../specs/001-webox-meal-ordering/acceptance.md#ac-us01-01) | 关联AC通过 |
| [REQ-007](../../specs/001-webox-meal-ordering/requirements-source.md#req-007) | [FR-007](../../specs/001-webox-meal-ordering/spec.md#fr-007) | [AC-US01-02](../../specs/001-webox-meal-ordering/acceptance.md#ac-us01-02) | 关联AC通过 |
| [REQ-008](../../specs/001-webox-meal-ordering/requirements-source.md#req-008) | [FR-007](../../specs/001-webox-meal-ordering/spec.md#fr-007) | [AC-US01-02](../../specs/001-webox-meal-ordering/acceptance.md#ac-us01-02) | 关联AC通过 |
| [REQ-009](../../specs/001-webox-meal-ordering/requirements-source.md#req-009) | [FR-006](../../specs/001-webox-meal-ordering/spec.md#fr-006)、[FR-007](../../specs/001-webox-meal-ordering/spec.md#fr-007) | [AC-US01-01](../../specs/001-webox-meal-ordering/acceptance.md#ac-us01-01)、[AC-US01-02](../../specs/001-webox-meal-ordering/acceptance.md#ac-us01-02) | 关联AC通过 |
| [REQ-010](../../specs/001-webox-meal-ordering/requirements-source.md#req-010) | [FR-006](../../specs/001-webox-meal-ordering/spec.md#fr-006) | [AC-US01-01](../../specs/001-webox-meal-ordering/acceptance.md#ac-us01-01) | 关联AC通过 |
| [REQ-011](../../specs/001-webox-meal-ordering/requirements-source.md#req-011) | [FR-006](../../specs/001-webox-meal-ordering/spec.md#fr-006) | [AC-US01-01](../../specs/001-webox-meal-ordering/acceptance.md#ac-us01-01) | 关联AC通过 |
| [REQ-012](../../specs/001-webox-meal-ordering/requirements-source.md#req-012) | [FR-004](../../specs/001-webox-meal-ordering/spec.md#fr-004) | [AC-US01-05](../../specs/001-webox-meal-ordering/acceptance.md#ac-us01-05) | 关联AC通过 |
| [REQ-013](../../specs/001-webox-meal-ordering/requirements-source.md#req-013) | [FR-010](../../specs/001-webox-meal-ordering/spec.md#fr-010) | [AC-US02-01](../../specs/001-webox-meal-ordering/acceptance.md#ac-us02-01) | 关联AC通过 |
| [REQ-014](../../specs/001-webox-meal-ordering/requirements-source.md#req-014) | [FR-010](../../specs/001-webox-meal-ordering/spec.md#fr-010)、[FR-003](../../specs/001-webox-meal-ordering/spec.md#fr-003) | [AC-US02-01](../../specs/001-webox-meal-ordering/acceptance.md#ac-us02-01)、[AC-US02-06](../../specs/001-webox-meal-ordering/acceptance.md#ac-us02-06) | 关联AC通过 |
| [REQ-015](../../specs/001-webox-meal-ordering/requirements-source.md#req-015) | [FR-011](../../specs/001-webox-meal-ordering/spec.md#fr-011) | [AC-US02-01](../../specs/001-webox-meal-ordering/acceptance.md#ac-us02-01) | 关联AC通过 |
| [REQ-016](../../specs/001-webox-meal-ordering/requirements-source.md#req-016) | [FR-013](../../specs/001-webox-meal-ordering/spec.md#fr-013)、[FR-011](../../specs/001-webox-meal-ordering/spec.md#fr-011) | [AC-US02-03](../../specs/001-webox-meal-ordering/acceptance.md#ac-us02-03)、[AC-US02-01](../../specs/001-webox-meal-ordering/acceptance.md#ac-us02-01) | 关联AC通过 |
| [REQ-017](../../specs/001-webox-meal-ordering/requirements-source.md#req-017) | [FR-015](../../specs/001-webox-meal-ordering/spec.md#fr-015)、[FR-003](../../specs/001-webox-meal-ordering/spec.md#fr-003) | [AC-US02-05](../../specs/001-webox-meal-ordering/acceptance.md#ac-us02-05)、[AC-US02-06](../../specs/001-webox-meal-ordering/acceptance.md#ac-us02-06) | 关联AC通过 |
| [REQ-018](../../specs/001-webox-meal-ordering/requirements-source.md#req-018) | [FR-012](../../specs/001-webox-meal-ordering/spec.md#fr-012) | [AC-US02-02](../../specs/001-webox-meal-ordering/acceptance.md#ac-us02-02) | 关联AC通过 |
| [REQ-019](../../specs/001-webox-meal-ordering/requirements-source.md#req-019) | [FR-012](../../specs/001-webox-meal-ordering/spec.md#fr-012) | [AC-US02-02](../../specs/001-webox-meal-ordering/acceptance.md#ac-us02-02) | 关联AC通过 |
| [REQ-020](../../specs/001-webox-meal-ordering/requirements-source.md#req-020) | [SC-003](../../specs/001-webox-meal-ordering/spec.md#sc-003) | [AC-SC-003](../../specs/001-webox-meal-ordering/acceptance.md#ac-sc-003) | 关联AC通过 |
| [REQ-021](../../specs/001-webox-meal-ordering/requirements-source.md#req-021) | [FR-014](../../specs/001-webox-meal-ordering/spec.md#fr-014)、[FR-015](../../specs/001-webox-meal-ordering/spec.md#fr-015) | [AC-US02-04](../../specs/001-webox-meal-ordering/acceptance.md#ac-us02-04)、[AC-US02-05](../../specs/001-webox-meal-ordering/acceptance.md#ac-us02-05) | 关联AC通过 |
| [REQ-022](../../specs/001-webox-meal-ordering/requirements-source.md#req-022) | [FR-015](../../specs/001-webox-meal-ordering/spec.md#fr-015) | [AC-US02-05](../../specs/001-webox-meal-ordering/acceptance.md#ac-us02-05) | 关联AC通过 |
| [REQ-023](../../specs/001-webox-meal-ordering/requirements-source.md#req-023) | [FR-015](../../specs/001-webox-meal-ordering/spec.md#fr-015)、[FR-003](../../specs/001-webox-meal-ordering/spec.md#fr-003) | [AC-US02-05](../../specs/001-webox-meal-ordering/acceptance.md#ac-us02-05)、[AC-US02-06](../../specs/001-webox-meal-ordering/acceptance.md#ac-us02-06) | 关联AC通过 |
| [REQ-024](../../specs/001-webox-meal-ordering/requirements-source.md#req-024) | [FR-014](../../specs/001-webox-meal-ordering/spec.md#fr-014) | [AC-US02-04](../../specs/001-webox-meal-ordering/acceptance.md#ac-us02-04) | 关联AC通过 |
| [REQ-025](../../specs/001-webox-meal-ordering/requirements-source.md#req-025) | [FR-014](../../specs/001-webox-meal-ordering/spec.md#fr-014) | [AC-US02-04](../../specs/001-webox-meal-ordering/acceptance.md#ac-us02-04) | 关联AC通过 |
| [REQ-026](../../specs/001-webox-meal-ordering/requirements-source.md#req-026) | [FR-016](../../specs/001-webox-meal-ordering/spec.md#fr-016) | [AC-US03-01](../../specs/001-webox-meal-ordering/acceptance.md#ac-us03-01) | 关联AC通过 |
| [REQ-027](../../specs/001-webox-meal-ordering/requirements-source.md#req-027) | [FR-016](../../specs/001-webox-meal-ordering/spec.md#fr-016) | [AC-US03-01](../../specs/001-webox-meal-ordering/acceptance.md#ac-us03-01) | 关联AC通过 |
| [REQ-028](../../specs/001-webox-meal-ordering/requirements-source.md#req-028) | [FR-016](../../specs/001-webox-meal-ordering/spec.md#fr-016) | [AC-US03-01](../../specs/001-webox-meal-ordering/acceptance.md#ac-us03-01) | 关联AC通过 |
| [REQ-029](../../specs/001-webox-meal-ordering/requirements-source.md#req-029) | [FR-016](../../specs/001-webox-meal-ordering/spec.md#fr-016) | [AC-US03-01](../../specs/001-webox-meal-ordering/acceptance.md#ac-us03-01) | 关联AC通过 |
| [REQ-030](../../specs/001-webox-meal-ordering/requirements-source.md#req-030) | [FR-018](../../specs/001-webox-meal-ordering/spec.md#fr-018) | [AC-US03-03](../../specs/001-webox-meal-ordering/acceptance.md#ac-us03-03) | 关联AC通过 |
| [REQ-031](../../specs/001-webox-meal-ordering/requirements-source.md#req-031) | [FR-019](../../specs/001-webox-meal-ordering/spec.md#fr-019) | [AC-US03-02](../../specs/001-webox-meal-ordering/acceptance.md#ac-us03-02) | 关联AC通过 |
| [REQ-032](../../specs/001-webox-meal-ordering/requirements-source.md#req-032) | [FR-020](../../specs/001-webox-meal-ordering/spec.md#fr-020) | [AC-US03-04](../../specs/001-webox-meal-ordering/acceptance.md#ac-us03-04) | 关联AC通过 |
| [REQ-033](../../specs/001-webox-meal-ordering/requirements-source.md#req-033) | [FR-020](../../specs/001-webox-meal-ordering/spec.md#fr-020) | [AC-US03-04](../../specs/001-webox-meal-ordering/acceptance.md#ac-us03-04) | 关联AC通过 |
| [REQ-034](../../specs/001-webox-meal-ordering/requirements-source.md#req-034) | [FR-021](../../specs/001-webox-meal-ordering/spec.md#fr-021) | [AC-US03-05](../../specs/001-webox-meal-ordering/acceptance.md#ac-us03-05) | 关联AC通过 |
| [REQ-035](../../specs/001-webox-meal-ordering/requirements-source.md#req-035) | [FR-020](../../specs/001-webox-meal-ordering/spec.md#fr-020)、[FR-036](../../specs/001-webox-meal-ordering/spec.md#fr-036) | [AC-US03-04](../../specs/001-webox-meal-ordering/acceptance.md#ac-us03-04)、[AC-US06-06](../../specs/001-webox-meal-ordering/acceptance.md#ac-us06-06) | 关联AC通过 |
| [REQ-036](../../specs/001-webox-meal-ordering/requirements-source.md#req-036) | [FR-027](../../specs/001-webox-meal-ordering/spec.md#fr-027) | [AC-US05-01](../../specs/001-webox-meal-ordering/acceptance.md#ac-us05-01) | 关联AC通过 |
| [REQ-037](../../specs/001-webox-meal-ordering/requirements-source.md#req-037) | [FR-028](../../specs/001-webox-meal-ordering/spec.md#fr-028) | [AC-US05-02](../../specs/001-webox-meal-ordering/acceptance.md#ac-us05-02) | 关联AC通过 |
| [REQ-038](../../specs/001-webox-meal-ordering/requirements-source.md#req-038) | [FR-027](../../specs/001-webox-meal-ordering/spec.md#fr-027)、[FR-029](../../specs/001-webox-meal-ordering/spec.md#fr-029) | [AC-US05-01](../../specs/001-webox-meal-ordering/acceptance.md#ac-us05-01)、[AC-US05-03](../../specs/001-webox-meal-ordering/acceptance.md#ac-us05-03) | 关联AC通过 |
| [REQ-039](../../specs/001-webox-meal-ordering/requirements-source.md#req-039) | [FR-027](../../specs/001-webox-meal-ordering/spec.md#fr-027)、[FR-029](../../specs/001-webox-meal-ordering/spec.md#fr-029) | [AC-US05-01](../../specs/001-webox-meal-ordering/acceptance.md#ac-us05-01)、[AC-US05-03](../../specs/001-webox-meal-ordering/acceptance.md#ac-us05-03) | 关联AC通过 |
| [REQ-040](../../specs/001-webox-meal-ordering/requirements-source.md#req-040) | [FR-027](../../specs/001-webox-meal-ordering/spec.md#fr-027)、[FR-041](../../specs/001-webox-meal-ordering/spec.md#fr-041) | [AC-US05-01](../../specs/001-webox-meal-ordering/acceptance.md#ac-us05-01)、[AC-US08-01](../../specs/001-webox-meal-ordering/acceptance.md#ac-us08-01) | 关联AC通过 |
| [REQ-041](../../specs/001-webox-meal-ordering/requirements-source.md#req-041) | [FR-027](../../specs/001-webox-meal-ordering/spec.md#fr-027)、[FR-030](../../specs/001-webox-meal-ordering/spec.md#fr-030) | [AC-US05-01](../../specs/001-webox-meal-ordering/acceptance.md#ac-us05-01)、[AC-US05-04](../../specs/001-webox-meal-ordering/acceptance.md#ac-us05-04) | 关联AC通过 |
| [REQ-042](../../specs/001-webox-meal-ordering/requirements-source.md#req-042) | [FR-029](../../specs/001-webox-meal-ordering/spec.md#fr-029) | [AC-US05-03](../../specs/001-webox-meal-ordering/acceptance.md#ac-us05-03) | 关联AC通过 |
| [REQ-043](../../specs/001-webox-meal-ordering/requirements-source.md#req-043) | [FR-022](../../specs/001-webox-meal-ordering/spec.md#fr-022) | [AC-US02-05](../../specs/001-webox-meal-ordering/acceptance.md#ac-us02-05) | 关联AC通过 |
| [REQ-044](../../specs/001-webox-meal-ordering/requirements-source.md#req-044) | [FR-023](../../specs/001-webox-meal-ordering/spec.md#fr-023) | [AC-US04-02](../../specs/001-webox-meal-ordering/acceptance.md#ac-us04-02) | 关联AC通过 |
| [REQ-045](../../specs/001-webox-meal-ordering/requirements-source.md#req-045) | [FR-024](../../specs/001-webox-meal-ordering/spec.md#fr-024) | [AC-US04-02](../../specs/001-webox-meal-ordering/acceptance.md#ac-us04-02) | 关联AC通过 |
| [REQ-046](../../specs/001-webox-meal-ordering/requirements-source.md#req-046) | [FR-025](../../specs/001-webox-meal-ordering/spec.md#fr-025) | [AC-US04-04](../../specs/001-webox-meal-ordering/acceptance.md#ac-us04-04) | 关联AC通过 |
| [REQ-047](../../specs/001-webox-meal-ordering/requirements-source.md#req-047) | [FR-025](../../specs/001-webox-meal-ordering/spec.md#fr-025) | [AC-US04-04](../../specs/001-webox-meal-ordering/acceptance.md#ac-us04-04) | 关联AC通过 |
| [REQ-048](../../specs/001-webox-meal-ordering/requirements-source.md#req-048) | [FR-031](../../specs/001-webox-meal-ordering/spec.md#fr-031) | [AC-US01-04](../../specs/001-webox-meal-ordering/acceptance.md#ac-us01-04) | 关联AC通过 |
| [REQ-049](../../specs/001-webox-meal-ordering/requirements-source.md#req-049) | [FR-008](../../specs/001-webox-meal-ordering/spec.md#fr-008) | [AC-US01-04](../../specs/001-webox-meal-ordering/acceptance.md#ac-us01-04) | 关联AC通过 |
| [REQ-050](../../specs/001-webox-meal-ordering/requirements-source.md#req-050) | [FR-031](../../specs/001-webox-meal-ordering/spec.md#fr-031) | [AC-US01-04](../../specs/001-webox-meal-ordering/acceptance.md#ac-us01-04) | 关联AC通过 |
| [REQ-051](../../specs/001-webox-meal-ordering/requirements-source.md#req-051) | [FR-032](../../specs/001-webox-meal-ordering/spec.md#fr-032) | [AC-US06-01](../../specs/001-webox-meal-ordering/acceptance.md#ac-us06-01) | 关联AC通过 |
| [REQ-052](../../specs/001-webox-meal-ordering/requirements-source.md#req-052) | [FR-032](../../specs/001-webox-meal-ordering/spec.md#fr-032) | [AC-US06-01](../../specs/001-webox-meal-ordering/acceptance.md#ac-us06-01) | 关联AC通过 |
| [REQ-053](../../specs/001-webox-meal-ordering/requirements-source.md#req-053) | [FR-033](../../specs/001-webox-meal-ordering/spec.md#fr-033) | [AC-US04-06](../../specs/001-webox-meal-ordering/acceptance.md#ac-us04-06) | 关联AC通过 |
| [REQ-054](../../specs/001-webox-meal-ordering/requirements-source.md#req-054) | [FR-034](../../specs/001-webox-meal-ordering/spec.md#fr-034) | [AC-US06-04](../../specs/001-webox-meal-ordering/acceptance.md#ac-us06-04) | 关联AC通过 |
| [REQ-055](../../specs/001-webox-meal-ordering/requirements-source.md#req-055) | [FR-034](../../specs/001-webox-meal-ordering/spec.md#fr-034) | [AC-US06-04](../../specs/001-webox-meal-ordering/acceptance.md#ac-us06-04) | 关联AC通过 |
| [REQ-056](../../specs/001-webox-meal-ordering/requirements-source.md#req-056) | [FR-035](../../specs/001-webox-meal-ordering/spec.md#fr-035) | [AC-US06-05](../../specs/001-webox-meal-ordering/acceptance.md#ac-us06-05) | 关联AC通过 |
| [REQ-057](../../specs/001-webox-meal-ordering/requirements-source.md#req-057) | [FR-037](../../specs/001-webox-meal-ordering/spec.md#fr-037) | [AC-US07-01](../../specs/001-webox-meal-ordering/acceptance.md#ac-us07-01) | 关联AC通过 |
| [REQ-058](../../specs/001-webox-meal-ordering/requirements-source.md#req-058) | [FR-037](../../specs/001-webox-meal-ordering/spec.md#fr-037) | [AC-US07-01](../../specs/001-webox-meal-ordering/acceptance.md#ac-us07-01) | 关联AC通过 |
| [REQ-059](../../specs/001-webox-meal-ordering/requirements-source.md#req-059) | [FR-037](../../specs/001-webox-meal-ordering/spec.md#fr-037) | [AC-US07-01](../../specs/001-webox-meal-ordering/acceptance.md#ac-us07-01) | 关联AC通过 |
| [REQ-060](../../specs/001-webox-meal-ordering/requirements-source.md#req-060) | [FR-038](../../specs/001-webox-meal-ordering/spec.md#fr-038) | [AC-US07-02](../../specs/001-webox-meal-ordering/acceptance.md#ac-us07-02) | 关联AC通过 |
| [REQ-061](../../specs/001-webox-meal-ordering/requirements-source.md#req-061) | [FR-039](../../specs/001-webox-meal-ordering/spec.md#fr-039) | [AC-US07-04](../../specs/001-webox-meal-ordering/acceptance.md#ac-us07-04) | 关联AC通过 |
| [REQ-062](../../specs/001-webox-meal-ordering/requirements-source.md#req-062) | [FR-038](../../specs/001-webox-meal-ordering/spec.md#fr-038)、[FR-017](../../specs/001-webox-meal-ordering/spec.md#fr-017) | [AC-US07-02](../../specs/001-webox-meal-ordering/acceptance.md#ac-us07-02)、[AC-US03-06](../../specs/001-webox-meal-ordering/acceptance.md#ac-us03-06) | 关联AC通过 |
| [REQ-063](../../specs/001-webox-meal-ordering/requirements-source.md#req-063) | [FR-040](../../specs/001-webox-meal-ordering/spec.md#fr-040) | [AC-US06-07](../../specs/001-webox-meal-ordering/acceptance.md#ac-us06-07) | 关联AC通过 |
| [REQ-064](../../specs/001-webox-meal-ordering/requirements-source.md#req-064) | [FR-041](../../specs/001-webox-meal-ordering/spec.md#fr-041) | [AC-US08-01](../../specs/001-webox-meal-ordering/acceptance.md#ac-us08-01) | 关联AC通过 |
| [REQ-065](../../specs/001-webox-meal-ordering/requirements-source.md#req-065) | [FR-041](../../specs/001-webox-meal-ordering/spec.md#fr-041) | [AC-US08-01](../../specs/001-webox-meal-ordering/acceptance.md#ac-us08-01) | 关联AC通过 |
| [REQ-066](../../specs/001-webox-meal-ordering/requirements-source.md#req-066) | [FR-042](../../specs/001-webox-meal-ordering/spec.md#fr-042) | [AC-US08-01](../../specs/001-webox-meal-ordering/acceptance.md#ac-us08-01) | 关联AC通过 |
| [REQ-067](../../specs/001-webox-meal-ordering/requirements-source.md#req-067) | [FR-042](../../specs/001-webox-meal-ordering/spec.md#fr-042) | [AC-US08-01](../../specs/001-webox-meal-ordering/acceptance.md#ac-us08-01) | 关联AC通过 |
| [REQ-068](../../specs/001-webox-meal-ordering/requirements-source.md#req-068) | [FR-041](../../specs/001-webox-meal-ordering/spec.md#fr-041)、[FR-042](../../specs/001-webox-meal-ordering/spec.md#fr-042) | [AC-US08-01](../../specs/001-webox-meal-ordering/acceptance.md#ac-us08-01) | 关联AC通过 |
| [REQ-069](../../specs/001-webox-meal-ordering/requirements-source.md#req-069) | [FR-043](../../specs/001-webox-meal-ordering/spec.md#fr-043) | [AC-US08-03](../../specs/001-webox-meal-ordering/acceptance.md#ac-us08-03) | 关联AC通过 |
| [REQ-070](../../specs/001-webox-meal-ordering/requirements-source.md#req-070) | [FR-043](../../specs/001-webox-meal-ordering/spec.md#fr-043) | [AC-US08-03](../../specs/001-webox-meal-ordering/acceptance.md#ac-us08-03) | 关联AC通过 |
| [REQ-071](../../specs/001-webox-meal-ordering/requirements-source.md#req-071) | [FR-043](../../specs/001-webox-meal-ordering/spec.md#fr-043)、[SC-010](../../specs/001-webox-meal-ordering/spec.md#sc-010) | [AC-US08-03](../../specs/001-webox-meal-ordering/acceptance.md#ac-us08-03)、[AC-SC-010](../../specs/001-webox-meal-ordering/acceptance.md#ac-sc-010) | 关联AC仍有待办 |
| [REQ-072](../../specs/001-webox-meal-ordering/requirements-source.md#req-072) | [FR-044](../../specs/001-webox-meal-ordering/spec.md#fr-044)、[DC-001](../../specs/001-webox-meal-ordering/delivery-constraints.md#dc-001) | [AC-US08-04](../../specs/001-webox-meal-ordering/acceptance.md#ac-us08-04)、[AC-DC-001](../../specs/001-webox-meal-ordering/acceptance.md#ac-dc-001) | 关联AC通过 |
| [REQ-073](../../specs/001-webox-meal-ordering/requirements-source.md#req-073) | [FR-045](../../specs/001-webox-meal-ordering/spec.md#fr-045) | [AC-US09-01](../../specs/001-webox-meal-ordering/acceptance.md#ac-us09-01) | 关联AC通过 |
| [REQ-074](../../specs/001-webox-meal-ordering/requirements-source.md#req-074) | [FR-045](../../specs/001-webox-meal-ordering/spec.md#fr-045)、[FR-047](../../specs/001-webox-meal-ordering/spec.md#fr-047) | [AC-US09-01](../../specs/001-webox-meal-ordering/acceptance.md#ac-us09-01) | 关联AC通过 |
| [REQ-075](../../specs/001-webox-meal-ordering/requirements-source.md#req-075) | [FR-045](../../specs/001-webox-meal-ordering/spec.md#fr-045)、[FR-047](../../specs/001-webox-meal-ordering/spec.md#fr-047) | [AC-US09-01](../../specs/001-webox-meal-ordering/acceptance.md#ac-us09-01) | 关联AC通过 |
| [REQ-076](../../specs/001-webox-meal-ordering/requirements-source.md#req-076) | [FR-045](../../specs/001-webox-meal-ordering/spec.md#fr-045)、[FR-047](../../specs/001-webox-meal-ordering/spec.md#fr-047) | [AC-US09-01](../../specs/001-webox-meal-ordering/acceptance.md#ac-us09-01) | 关联AC通过 |
| [REQ-077](../../specs/001-webox-meal-ordering/requirements-source.md#req-077) | [FR-045](../../specs/001-webox-meal-ordering/spec.md#fr-045)、[FR-047](../../specs/001-webox-meal-ordering/spec.md#fr-047) | [AC-US09-01](../../specs/001-webox-meal-ordering/acceptance.md#ac-us09-01) | 关联AC通过 |
| [REQ-078](../../specs/001-webox-meal-ordering/requirements-source.md#req-078) | [FR-045](../../specs/001-webox-meal-ordering/spec.md#fr-045)、[FR-047](../../specs/001-webox-meal-ordering/spec.md#fr-047) | [AC-US09-01](../../specs/001-webox-meal-ordering/acceptance.md#ac-us09-01) | 关联AC通过 |
| [REQ-079](../../specs/001-webox-meal-ordering/requirements-source.md#req-079) | [FR-046](../../specs/001-webox-meal-ordering/spec.md#fr-046) | [AC-US09-03](../../specs/001-webox-meal-ordering/acceptance.md#ac-us09-03) | 关联AC通过 |
| [REQ-080](../../specs/001-webox-meal-ordering/requirements-source.md#req-080) | [FR-046](../../specs/001-webox-meal-ordering/spec.md#fr-046) | [AC-US09-03](../../specs/001-webox-meal-ordering/acceptance.md#ac-us09-03) | 关联AC通过 |
| [REQ-081](../../specs/001-webox-meal-ordering/requirements-source.md#req-081) | [FR-011](../../specs/001-webox-meal-ordering/spec.md#fr-011) | [AC-US02-01](../../specs/001-webox-meal-ordering/acceptance.md#ac-us02-01) | 关联AC通过 |
| [REQ-082](../../specs/001-webox-meal-ordering/requirements-source.md#req-082) | [FR-011](../../specs/001-webox-meal-ordering/spec.md#fr-011)、[FR-027](../../specs/001-webox-meal-ordering/spec.md#fr-027) | [AC-US02-01](../../specs/001-webox-meal-ordering/acceptance.md#ac-us02-01)、[AC-US05-01](../../specs/001-webox-meal-ordering/acceptance.md#ac-us05-01) | 关联AC通过 |
| [REQ-083](../../specs/001-webox-meal-ordering/requirements-source.md#req-083) | [FR-011](../../specs/001-webox-meal-ordering/spec.md#fr-011)、[FR-027](../../specs/001-webox-meal-ordering/spec.md#fr-027) | [AC-US02-01](../../specs/001-webox-meal-ordering/acceptance.md#ac-us02-01)、[AC-US05-01](../../specs/001-webox-meal-ordering/acceptance.md#ac-us05-01) | 关联AC通过 |
| [REQ-084](../../specs/001-webox-meal-ordering/requirements-source.md#req-084) | [FR-003](../../specs/001-webox-meal-ordering/spec.md#fr-003) | [AC-US02-06](../../specs/001-webox-meal-ordering/acceptance.md#ac-us02-06) | 关联AC通过 |
| [REQ-085](../../specs/001-webox-meal-ordering/requirements-source.md#req-085) | [DC-006](../../specs/001-webox-meal-ordering/delivery-constraints.md#dc-006) | [AC-DC-006](../../specs/001-webox-meal-ordering/acceptance.md#ac-dc-006) | 关联AC通过 |
| [REQ-086](../../specs/001-webox-meal-ordering/requirements-source.md#req-086) | [DC-001](../../specs/001-webox-meal-ordering/delivery-constraints.md#dc-001)、[DC-002](../../specs/001-webox-meal-ordering/delivery-constraints.md#dc-002)、[DC-003](../../specs/001-webox-meal-ordering/delivery-constraints.md#dc-003) | [AC-DC-001](../../specs/001-webox-meal-ordering/acceptance.md#ac-dc-001)、[AC-DC-002](../../specs/001-webox-meal-ordering/acceptance.md#ac-dc-002)、[AC-DC-003](../../specs/001-webox-meal-ordering/acceptance.md#ac-dc-003) | 关联AC通过 |
| [REQ-087](../../specs/001-webox-meal-ordering/requirements-source.md#req-087) | [DC-007](../../specs/001-webox-meal-ordering/delivery-constraints.md#dc-007) | [AC-DC-007](../../specs/001-webox-meal-ordering/acceptance.md#ac-dc-007) | 关联AC通过 |
| [REQ-088](../../specs/001-webox-meal-ordering/requirements-source.md#req-088) | [DC-002](../../specs/001-webox-meal-ordering/delivery-constraints.md#dc-002) | [AC-DC-002](../../specs/001-webox-meal-ordering/acceptance.md#ac-dc-002) | 关联AC通过 |
| [REQ-089](../../specs/001-webox-meal-ordering/requirements-source.md#req-089) | [DC-003](../../specs/001-webox-meal-ordering/delivery-constraints.md#dc-003)、[FR-009](../../specs/001-webox-meal-ordering/spec.md#fr-009) | [AC-DC-003](../../specs/001-webox-meal-ordering/acceptance.md#ac-dc-003)、[AC-SC-006](../../specs/001-webox-meal-ordering/acceptance.md#ac-sc-006) | 关联AC通过 |
| [REQ-090](../../specs/001-webox-meal-ordering/requirements-source.md#req-090) | [DC-004](../../specs/001-webox-meal-ordering/delivery-constraints.md#dc-004) | [AC-DC-004](../../specs/001-webox-meal-ordering/acceptance.md#ac-dc-004) | 关联AC通过 |
| [REQ-091](../../specs/001-webox-meal-ordering/requirements-source.md#req-091) | [DC-007](../../specs/001-webox-meal-ordering/delivery-constraints.md#dc-007) | [AC-DC-007](../../specs/001-webox-meal-ordering/acceptance.md#ac-dc-007) | 关联AC通过 |
| [REQ-092](../../specs/001-webox-meal-ordering/requirements-source.md#req-092) | [DC-008](../../specs/001-webox-meal-ordering/delivery-constraints.md#dc-008) | [AC-DC-008](../../specs/001-webox-meal-ordering/acceptance.md#ac-dc-008) | 关联AC通过 |
| [REQ-093](../../specs/001-webox-meal-ordering/requirements-source.md#req-093) | [DC-008](../../specs/001-webox-meal-ordering/delivery-constraints.md#dc-008) | [AC-DC-008](../../specs/001-webox-meal-ordering/acceptance.md#ac-dc-008) | 关联AC通过 |
| [REQ-094](../../specs/001-webox-meal-ordering/requirements-source.md#req-094) | [DC-009](../../specs/001-webox-meal-ordering/delivery-constraints.md#dc-009) | [AC-DC-009](../../specs/001-webox-meal-ordering/acceptance.md#ac-dc-009) | 关联AC通过 |
| [REQ-095](../../specs/001-webox-meal-ordering/requirements-source.md#req-095) | [DC-010](../../specs/001-webox-meal-ordering/delivery-constraints.md#dc-010) | [AC-DC-010](../../specs/001-webox-meal-ordering/acceptance.md#ac-dc-010) | 关联AC通过 |
| [REQ-096](../../specs/001-webox-meal-ordering/requirements-source.md#req-096) | [DC-010](../../specs/001-webox-meal-ordering/delivery-constraints.md#dc-010) | [AC-DC-010](../../specs/001-webox-meal-ordering/acceptance.md#ac-dc-010) | 关联AC通过 |
| [REQ-097](../../specs/001-webox-meal-ordering/requirements-source.md#req-097) | [DC-007](../../specs/001-webox-meal-ordering/delivery-constraints.md#dc-007)、[DC-009](../../specs/001-webox-meal-ordering/delivery-constraints.md#dc-009)、[DC-010](../../specs/001-webox-meal-ordering/delivery-constraints.md#dc-010) | [AC-DC-007](../../specs/001-webox-meal-ordering/acceptance.md#ac-dc-007)、[AC-DC-009](../../specs/001-webox-meal-ordering/acceptance.md#ac-dc-009)、[AC-DC-010](../../specs/001-webox-meal-ordering/acceptance.md#ac-dc-010) | 关联AC通过 |
| [REQ-098](../../specs/001-webox-meal-ordering/requirements-source.md#req-098) | [DC-001](../../specs/001-webox-meal-ordering/delivery-constraints.md#dc-001) | [AC-DC-001](../../specs/001-webox-meal-ordering/acceptance.md#ac-dc-001) | 关联AC通过 |
| [REQ-099](../../specs/001-webox-meal-ordering/requirements-source.md#req-099) | [DC-001](../../specs/001-webox-meal-ordering/delivery-constraints.md#dc-001)、[DC-010](../../specs/001-webox-meal-ordering/delivery-constraints.md#dc-010) | [AC-DC-001](../../specs/001-webox-meal-ordering/acceptance.md#ac-dc-001)、[AC-DC-010](../../specs/001-webox-meal-ordering/acceptance.md#ac-dc-010) | 关联AC通过 |
| [REQ-100](../../specs/001-webox-meal-ordering/requirements-source.md#req-100) | [DC-001](../../specs/001-webox-meal-ordering/delivery-constraints.md#dc-001)、[DC-009](../../specs/001-webox-meal-ordering/delivery-constraints.md#dc-009) | [AC-DC-001](../../specs/001-webox-meal-ordering/acceptance.md#ac-dc-001)、[AC-DC-009](../../specs/001-webox-meal-ordering/acceptance.md#ac-dc-009) | 关联AC通过 |
| [REQ-101](../../specs/001-webox-meal-ordering/requirements-source.md#req-101) | [DC-001](../../specs/001-webox-meal-ordering/delivery-constraints.md#dc-001)、[DC-009](../../specs/001-webox-meal-ordering/delivery-constraints.md#dc-009) | [AC-DC-001](../../specs/001-webox-meal-ordering/acceptance.md#ac-dc-001)、[AC-DC-009](../../specs/001-webox-meal-ordering/acceptance.md#ac-dc-009) | 关联AC通过 |
| [REQ-102](../../specs/001-webox-meal-ordering/requirements-source.md#req-102) | [DC-001](../../specs/001-webox-meal-ordering/delivery-constraints.md#dc-001)、[DC-009](../../specs/001-webox-meal-ordering/delivery-constraints.md#dc-009) | [AC-DC-001](../../specs/001-webox-meal-ordering/acceptance.md#ac-dc-001)、[AC-DC-009](../../specs/001-webox-meal-ordering/acceptance.md#ac-dc-009) | 关联AC通过 |
| [REQ-103](../../specs/001-webox-meal-ordering/requirements-source.md#req-103) | [DC-009](../../specs/001-webox-meal-ordering/delivery-constraints.md#dc-009) | [AC-DC-009](../../specs/001-webox-meal-ordering/acceptance.md#ac-dc-009) | 关联AC通过 |
| [REQ-104](../../specs/001-webox-meal-ordering/requirements-source.md#req-104) | [FR-018](../../specs/001-webox-meal-ordering/spec.md#fr-018)、[FR-038](../../specs/001-webox-meal-ordering/spec.md#fr-038) | [AC-US03-03](../../specs/001-webox-meal-ordering/acceptance.md#ac-us03-03)、[AC-US07-02](../../specs/001-webox-meal-ordering/acceptance.md#ac-us07-02) | 关联AC通过 |
| [REQ-105](../../specs/001-webox-meal-ordering/requirements-source.md#req-105) | [FR-002](../../specs/001-webox-meal-ordering/spec.md#fr-002)、[SC-002](../../specs/001-webox-meal-ordering/spec.md#sc-002) | [AC-SC-002](../../specs/001-webox-meal-ordering/acceptance.md#ac-sc-002) | 关联AC通过 |
| [REQ-106](../../specs/001-webox-meal-ordering/requirements-source.md#req-106) | [SC-003](../../specs/001-webox-meal-ordering/spec.md#sc-003) | [AC-SC-003](../../specs/001-webox-meal-ordering/acceptance.md#ac-sc-003) | 关联AC通过 |
| [REQ-107](../../specs/001-webox-meal-ordering/requirements-source.md#req-107) | [FR-015](../../specs/001-webox-meal-ordering/spec.md#fr-015)、[FR-018](../../specs/001-webox-meal-ordering/spec.md#fr-018) | [AC-US02-05](../../specs/001-webox-meal-ordering/acceptance.md#ac-us02-05)、[AC-US03-03](../../specs/001-webox-meal-ordering/acceptance.md#ac-us03-03) | 关联AC通过 |
| [REQ-108](../../specs/001-webox-meal-ordering/requirements-source.md#req-108) | [FR-003](../../specs/001-webox-meal-ordering/spec.md#fr-003) | [AC-US02-06](../../specs/001-webox-meal-ordering/acceptance.md#ac-us02-06) | 关联AC通过 |
| [REQ-109](../../specs/001-webox-meal-ordering/requirements-source.md#req-109) | [FR-004](../../specs/001-webox-meal-ordering/spec.md#fr-004) | [AC-US01-05](../../specs/001-webox-meal-ordering/acceptance.md#ac-us01-05) | 关联AC通过 |
| [REQ-110](../../specs/001-webox-meal-ordering/requirements-source.md#req-110) | [DC-004](../../specs/001-webox-meal-ordering/delivery-constraints.md#dc-004) | [AC-DC-004](../../specs/001-webox-meal-ordering/acceptance.md#ac-dc-004) | 关联AC通过 |
| [REQ-111](../../specs/001-webox-meal-ordering/requirements-source.md#req-111) | [FR-018](../../specs/001-webox-meal-ordering/spec.md#fr-018) | [AC-US03-03](../../specs/001-webox-meal-ordering/acceptance.md#ac-us03-03) | 关联AC通过 |
| [REQ-112](../../specs/001-webox-meal-ordering/requirements-source.md#req-112) | [FR-004](../../specs/001-webox-meal-ordering/spec.md#fr-004) | [AC-US01-05](../../specs/001-webox-meal-ordering/acceptance.md#ac-us01-05) | 关联AC通过 |
| [REQ-113](../../specs/001-webox-meal-ordering/requirements-source.md#req-113) | [FR-008](../../specs/001-webox-meal-ordering/spec.md#fr-008) | [AC-US01-04](../../specs/001-webox-meal-ordering/acceptance.md#ac-us01-04) | 关联AC通过 |
| [REQ-114](../../specs/001-webox-meal-ordering/requirements-source.md#req-114) | [FR-038](../../specs/001-webox-meal-ordering/spec.md#fr-038) | [AC-US07-02](../../specs/001-webox-meal-ordering/acceptance.md#ac-us07-02) | 关联AC通过 |
| [REQ-115](../../specs/001-webox-meal-ordering/requirements-source.md#req-115) | [DC-004](../../specs/001-webox-meal-ordering/delivery-constraints.md#dc-004) | [AC-DC-004](../../specs/001-webox-meal-ordering/acceptance.md#ac-dc-004) | 关联AC通过 |
| [REQ-116](../../specs/001-webox-meal-ordering/requirements-source.md#req-116) | [FR-018](../../specs/001-webox-meal-ordering/spec.md#fr-018)、[FR-038](../../specs/001-webox-meal-ordering/spec.md#fr-038) | [AC-US03-03](../../specs/001-webox-meal-ordering/acceptance.md#ac-us03-03)、[AC-US07-02](../../specs/001-webox-meal-ordering/acceptance.md#ac-us07-02) | 关联AC通过 |
| [REQ-117](../../specs/001-webox-meal-ordering/requirements-source.md#req-117) | [DC-008](../../specs/001-webox-meal-ordering/delivery-constraints.md#dc-008) | [AC-DC-008](../../specs/001-webox-meal-ordering/acceptance.md#ac-dc-008) | 关联AC通过 |
| [REQ-118](../../specs/001-webox-meal-ordering/requirements-source.md#req-118) | [DC-001](../../specs/001-webox-meal-ordering/delivery-constraints.md#dc-001)、[DC-003](../../specs/001-webox-meal-ordering/delivery-constraints.md#dc-003)、[DC-004](../../specs/001-webox-meal-ordering/delivery-constraints.md#dc-004) | [AC-DC-001](../../specs/001-webox-meal-ordering/acceptance.md#ac-dc-001)、[AC-DC-003](../../specs/001-webox-meal-ordering/acceptance.md#ac-dc-003)、[AC-DC-004](../../specs/001-webox-meal-ordering/acceptance.md#ac-dc-004) | 关联AC通过 |
