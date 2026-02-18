# RAI Mod (NeoForge 1.21.1)

Серверный мод, который создаёт «симулированных игроков» с собственной памятью, целями и долгосрочным прогрессом.

## Реализовано

- Серверный конфиг с hot-reload, лимитами популяции, динамической прогрузкой чанков, порогом рейда и scatter для стрельбы.
- `SimulatedSurvivor` как обёртка над `FakePlayer` + `PathfinderMob`-тело с `SmoothLookControl` и `JumpControl`.
- Utility-based decision engine и реальная формула оценки рейда.
- Баритон-интеграция через `ICustomGoalProcess` для целей лута/рейда/ретрита.
- TacZ lead-shot расчёт с упреждением, поправкой на падение пули и human-like scatter от Physical Stats.
- Сканер опасных блоков в 120° FOV через `VoxelShape`; детект contact mine из SecurityCraft.
- Серверная загрузка чанков тикетами с аварийным снижением радиуса до `1`, если средний tick > `45ms`.
- Persistence памяти и целей в `/world/rai_players/<uuid>.json`.
- Троттлинг лута 1–2 тика на предмет через cooldown в состоянии бота.
