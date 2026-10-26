# VpEgs

**Современный VPN‑клиент для Android** — подписки, Reality / VLESS / VMess / Trojan / SS / Hysteria2, обход приложений, виджет и плитка в шторке.

**Версия:** `1.7.0` · package `com.vpegs.ms`

<p align="center">
  <a href="https://github.com/VegsPro/VpEgs/releases/latest"><img src="https://img.shields.io/github/v/release/VegsPro/VpEgs?label=Release&color=0ea5e9" alt="Release"></a>
  <a href="https://github.com/VegsPro/VpEgs/actions"><img src="https://img.shields.io/github/actions/workflow/status/VegsPro/VpEgs/android.yml?branch=main&label=Build" alt="Build"></a>
  <a href="https://t.me/mstrblock"><img src="https://img.shields.io/badge/Telegram-@mstrblock-26A5E4?logo=telegram" alt="Telegram"></a>
  <img src="https://img.shields.io/badge/Android-10%2B-3DDC84?logo=android" alt="Android">
  <img src="https://img.shields.io/badge/Package-com.vpegs.ms-6366f1" alt="Package">
</p>

---

## Скачать

| | Ссылка |
|---|---|
| **Последний APK** | [Releases → Latest](https://github.com/VegsPro/VpEgs/releases/latest) |
| Прямая загрузка | [app-debug.apk](https://github.com/VegsPro/VpEgs/releases/latest/download/app-debug.apk) |
| Все версии | [Releases](https://github.com/VegsPro/VpEgs/releases) |
| CI‑сборки | [GitHub Actions](https://github.com/VegsPro/VpEgs/actions) |

> Установка: разрешите установку из этого источника в настройках безопасности телефона.

---

## Зачем VpEgs

- **Системный VPN** — в списке «Сеть и интернет → VPN»
- **Подписки** — ссылка, серверы, трафик и срок
- **Протоколы** — VLESS + Reality, VMess, Trojan, Shadowsocks, Hysteria2, TUIC
- **Обход приложений** — глобально или выборочно (напрямую / через VPN)
- **Пинг и «Лучший»** — выбор сервера с минимальной задержкой
- **Виджет** и **плитка V** в шторке быстрых настроек
- **Темы** — тёмные и светлые (синяя, фиолетовая и др.)
- **Без аккаунта** — данные только на устройстве

---

## Возможности

| Раздел | Содержание |
|--------|------------|
| **Главная** | ON/OFF, сервер, пинг, публичный IP, трафик, «Лучший» |
| **Серверы** | Группы по подпискам, сортировка, exit‑IP после проверки |
| **Подписки** | Добавление по URL, лимит, авто- и фоновое обновление |
| **Приложения** | Маршрутизация: VPN или напрямую |
| **Настройки** | Темы, пинг, отображение протокола/IP, Telegram |
| **Виджет** | Вкл/выкл, пинг, обновление, лучший сервер |
| **Шторка** | Плитка **V** (иконка-буква); долгое нажатие — открыть приложение |

---

## Быстрый старт

1. Скачайте APK: [latest release](https://github.com/VegsPro/VpEgs/releases/latest).
2. **Подписки** → **+** → вставьте ссылку → **Добавить**.
3. Разрешите VPN, когда система спросит.
4. **Главная** → кнопка **ON** или выбор сервера во вкладке **Серверы**.
5. По желанию: виджет на рабочий стол и плитка **V** в шторке.

### Обход приложений

1. Вкладка **Приложения**.
2. **VPN** — трафик через туннель; **Напрямую** — мимо VPN.
3. После смены списка переподключите VPN (или дождитесь авто‑реконнекта).

> Госуслуги и банки могут показывать «обнаружен VPN» по факту системного интерфейса, даже если трафик приложения идёт напрямую.

---

## Ссылки

| | |
|---|---|
| Telegram | [**@mstrblock**](https://t.me/mstrblock) |
| GitHub | [VegsPro/VpEgs](https://github.com/VegsPro/VpEgs) |
| Issues | [Сообщить о проблеме](https://github.com/VegsPro/VpEgs/issues) |

---

## Сборка

```bash
git clone https://github.com/VegsPro/VpEgs.git
cd VpEgs
./gradlew assembleDebug
# app/build/outputs/apk/debug/app-debug.apk
```

JDK 17+, Android SDK, minSdk 29 (Android 10+).

---

## Важно

Личное использование со своими подписками. Без аналитики и регистрации. Качество связи зависит от вашей подписки и сети.

<p align="center">
  <a href="https://t.me/mstrblock">@mstrblock</a> · <a href="https://github.com/VegsPro/VpEgs/releases/latest">Скачать APK</a>
</p>
