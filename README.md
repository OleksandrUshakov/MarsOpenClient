# MarsOpenClient — Відкритий Android-клієнт для Marstek Open API (Rev 3.1)

**MarsOpenClient** — вільний (FOSS, ліцензія `GPL-3.0-or-later`) Android-додаток для локального моніторингу та керування системами збереження енергії **Marstek** (`Venus A`, `Venus C`, `Venus D`, `Venus E`, `Venus E mini`) через локальну мережу (LAN) за протоколом **UDP JSON-RPC** без хмарних сервісів і пропрієтарних бібліотек.

Проєкт повністю підготовлений до публікації в каталозі **F-Droid**:
- Використовує виключно відкриті бібліотеки (`AndroidX`, `Jetpack Compose Material 3`, `kotlinx.serialization`) з Maven Central та Google Maven.
- У `app/build.gradle.kts` вимкнено блок шифрованих метаданих Google (`dependenciesInfo { includeInApk = false; includeInBundle = false }`), що є обов'язковою вимогою сканера F-Droid.
- Додано метадані **Fastlane** (`fastlane/metadata/android/uk` та `en-US`) і готовий рецепт збірки `fdroid/org.marsopenclient.yml`.

---

## Покриття Marstek Device Open API (Rev 3.1)

Клієнт реалізує **100% компонентів і команд**, описаних в офіційній документації **Marstek Device Open API (Rev 3.1)**:

| Розділ | Компонент / Метод | Опис функціоналу в додатку |
|---|---|---|
| **2.1 / 2.2** | **UDP Transport & JSON-RPC** | Комунікація по LAN UDP (порт за замовчуванням `30000`, підтримка рекомендованих портів `49152–65535`). Декодування помилок `-32700`..`-32099` та автоматичне виправлення багів прошивки (наприклад, `"set_result": ture`). |
| **2.2.2 & 3.1.1** | `Marstek.GetDevice` | Широкомовний пошук пристроїв у локальній мережі (UDP Broadcast з `ble_mac="0"` або за конкретним MAC) та отримання моделі (`device`), версії прошивки (`ver`), `ble_mac`, `wifi_mac`, `wifi_name`, `ip`. |
| **3.2.1** | `Wifi.GetStatus` | Перегляд мережевих параметрів пристрою: `ssid`, `rssi` (dBm з оцінкою якості сигналу), `wifi_mac`, `sta_ip`, `sta_gate`, `sta_mask`, `sta_dns`. |
| **3.3.1** | `BLE.GetStatus` | Перевірка стану з'єднання Bluetooth (`state`, `ble_mac`). |
| **3.4.1** | `Bat.GetStatus` | Стан батареї: заряд `soc` (%), прапорці дозволу заряду (`charg_flag`) та розряду (`dischrg_flag`), температура `bat_temp` (°C), залишкова (`bat_capacity`) та номінальна (`rated_capacity`) ємність (Wh). |
| **3.5.1** | `PV.GetStatus` | Моніторинг сонячних панелей (для `Venus A / D`): підтримка як 1-канального формату, так і 4-канального MPPT (`pv1`..`pv4` — потужність W, напруга V, струм A, статус `1: Work` / `0: Standby`). |
| **3.6.1** | `ES.GetStatus` | Електричні показники системи: `bat_soc`, `bat_cap`, `pv_power`, `ongrid_power`, `offgrid_power`, `bat_power`, а також накопичена енергія `total_pv_energy` (`0.01*kWh` та перерахунок у `kWh`), `total_grid_output_energy`, `total_grid_input_energy`, `total_load_energy`. |
| **3.6.2** | `ES.SetMode` | Конфігурація всіх 5 режимів роботи: **Auto** (`auto_cfg`), **AI** (`ai_cfg`), **Manual** (`manual_cfg`: слоти `0–9` або `0–5`, `start_time`, `end_time`, візуальний конструктор 7-бітної маски днів тижня `week_set` `0–127`, `power`, `enable`, `manual_set` `0..3` для Venus E mini), **Passive** (`passive_cfg`: `power` + таймер `cd_time`), **UPS** (`ups_cfg`). |
| **3.6.3** | `ES.GetMode` | Отримання поточного режиму роботи, потужності та показників трансформатора струму CT (`ct_state`, фази `a_power`, `b_power`, `c_power`, `total_power`, `input_energy`, `output_energy`). |
| **3.7.1** | `EM.GetStatus` | Опитування лічильника енергії / трансформатора струму (CT): `ct_state`, потужність по фазах `A`, `B`, `C`, сумарна потужність та накопичена вхідна/вихідна енергія (`0.1 Wh`). |
| **3.8.1** | `DOD.SET` | Налаштування глибини розряду (Depth of Discharge) у діапазоні `30–88%` (за замовчуванням `88%`). |
| **3.8.2** | `Ble.Adv` (`Ble_block`) | Керування трансляцією Bluetooth: `0` — увімкнути трансляцію, `1` — заблокувати/вимкнути трансляцію. |
| **3.8.3** | `Led.Ctrl` | Керування світлодіодною індикацією на панелі пристрою (`1` — увімкнено, `0` — вимкнено). |
| **3.8.4** | `Set.Ver` (`Version_Set`) | Встановлення версії потужності заряду/розряду: `800W`, `1200W (VA)`, `1500W (VA)`, `2200W (VD)`, `2500W (VE)` або довільного значення. |
| **3.8.5** | `Reset.Factory` | Скидання до заводських налаштувань із діалогом підтвердження: тип `1` (очистити накопичені дані) та тип `2` (зберегти накопичені дані). |
| **Розділ IV** | **Профілі пристроїв + Симулятор** | Врахування особливостей `Venus C/E`, `Venus D/A` та `Venus E mini`, вбудований локальний симулятор усіх трьох моделей для офлайн-перевірки та інтерактивна UDP консоль. |

---

## Збірка та запуск

```bash
# Запуск модульних тестів протоколу Marstek API Rev 3.1
./gradlew testDebugUnitTest

# Збірка Debug та Release APK
./gradlew assembleDebug assembleRelease
```
