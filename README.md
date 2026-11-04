# ElytrianNerf 🇷🇺

**Язык / Language:** 🇷🇺 конфиг, комментарии в нём и сообщения игрокам на русском (сообщения меняются в `messages.*`). Код и логи плагина тоже на русском.

Небольшой **аддон для [Origins-Reborn](https://github.com/cometcake575/Origins-Reborn)** (Paper 1.20+), который ослабляет способность Элитриана `origins:elytrian`: после удара и после воды полёт временно недоступен, в полёте расходуется сытость.

> ⚠️ Это неофициальный аддон. Он не связан с автором Origins-Reborn и не содержит его кода: плагин лишь обращается к API Origins-Reborn через рефлексию. Origins-Reborn нужно ставить отдельно ([Modrinth](https://modrinth.com/plugin/origins-reborn), [GitHub](https://github.com/cometcake575/Origins-Reborn)). Все права на оригинальный плагин принадлежат его автору.

*English:* the config, its comments and the in-game messages are in Russian (messages are editable in `messages.*`). An unofficial add-on for Origins-Reborn that nerfs the Elytrian `origins:elytrian` ability (flight lock after being hit / leaving water, hunger "fuel" while gliding, creative-flight fix). It requires Origins-Reborn and is not affiliated with its author.

## Возможности

- **Удар блокирует полёт.** После удара другим игроком (в ближнем бою) Элитриан не может летать N секунд. Стрелы и шипы не считаются. Режимы: только игроки / все мобы / только мобы, опционально только голой рукой.
- **Вода.** В воде и ещё N секунд после выхода из неё элитры недоступны.
- **«Топливо».** В полёте расходуется сытость; когда она на нуле, выдаётся эффект «Голод».
- **Фикс креатива.** В креативе двойной прыжок у Элитриана даёт обычный креативный полёт, а не элитры Origins.
- **Настоящие элитры.** Ограничения к ним по умолчанию не применяются (настраивается), а в слоте нагрудника они могут давать бонус брони как у железного нагрудника (+6).

## Установка

1. Установи [Origins-Reborn](https://modrinth.com/plugin/origins-reborn) на Paper 1.20+.
2. Скачай `ElytrianNerf-<версия>.jar` из раздела [Releases](../../releases) и положи в `plugins/`.
3. Перезапусти сервер. В консоли должна появиться строка «Подключён Origins-Reborn…».
4. Настрой `plugins/ElytrianNerf/config.yml`.

Без Origins-Reborn плагин ограничивает всех игроков без настоящих элитр.

## Настройка

Все параметры и комментарии лежат в [`config.yml`](src/main/resources/config.yml): `hit.*` (удары), `water.*` (вода), `fuel.*` (сытость), `real-elytra-armor.*` (броня), `creative-flight-fix`, `messages.*`.

## Сборка

Нужны JDK 17+ и Maven:

```bash
mvn package
```

Готовый jar будет в `target/`.

## Благодарности

- [Origins-Reborn](https://github.com/cometcake575/Origins-Reborn) (cometcake575 / Starshooter), без него этого аддона не существовало бы.
- Идея способностей: мод [Origins](https://github.com/apace100/origins-fabric) от apace100.

## Лицензия

[MIT](LICENSE) (только для кода этого аддона, не для Origins-Reborn).
