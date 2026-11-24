# Примечания к адаптации Expensive 3.1

Основано на предоставленном архиве исходников. В `functions/impl/render/HUD.java` перечислены восемь элементов: watermark, array list, coordinates, effects, staff list, keybinds, target info и armor. Были изучены соответствующие renderer-классы: `WatermarkRenderer`, `ArrayListRenderer`, `CoordsRenderer`, `PotionRenderer`, `StaffListRenderer`, `KeyBindRenderer`, `TargetInfoRenderer`, `ArmorRenderer`.

В MiniAssist портированы композиция и роли этих элементов, но не private assets, логотипы, SF UI fonts или старые Forge 1.16.5 классы. Порт написан для Fabric/Yarn 1.21.4.

Дополнительно из списка функций источника в проект добавлены независимые client-side реализации AutoSprint, InventoryMove, AutoTool и Zoom. Это не построчное копирование исходников Expensive.

Ограничения: StaffList основан на серверных scoreboard данными и эвристиках; он может ошибаться. InventoryMove зависит от поведения конкретного контейнера/сервера. Полная компиляция должна быть проверена GitHub Actions.
