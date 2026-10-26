# VpEgs

**Современный VPN‑клиент для Android** — подписки, Reality / VLESS / VMess / Trojan / SS / Hysteria2, обход приложений, виджет и плитка в шторке.

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
| Прямая загрузка APK | [app-debug.apk (latest release)](https://github.com/VegsPro/VpEgs/releases/latest/download/app-debug.apk) |
| История версий | [Все релизы](https://github.com/VegsPro/VpEgs/releases) |
| CI‑сборки | [GitHub Actions](https://github.com/VegsPro/VpEgs/actions) |

> Установка с неизвестных источников: **Настройки → Безопасность → Установка из этого источника**.

---

## Зачем VpEgs

- **Системный VPN** — появляется в списке «Сеть и интернет → VPN», как обычный клиент
- **Подписки** — одна ссылка, список серверов, трафик и срок (как в HAPP)
- **Современные протоколы** — VLESS + Reality, VMess, Trojan, Shadowsocks, Hysteria2, TUIC
- **Обход приложений** — глобальный режим или только выбранные; банки/Госуслуги можно пустить напрямую
- **Удобство** — «Лучший» сервер по пингу, виджет, плитка **V** в шторке быстрых настроек
- **Glass UI** — тёмные и светлые темы (синяя, фиолетовая, зелёная…)
- **Без аккаунта** — всё на устройстве, свои подписки

---

## Возможности

| Раздел | Что умеет |
|--------|-----------|
| **Главная** | Вкл/Выкл, текущий сервер, пинг, IP, трафик, «Лучший» |
| **Серверы** | Группы по подпискам, сортировка А–Я / пинг, IP после проверки |
| **Подписки** | Добавление по ссылке, лимит трафика, авто- и фоновое обновление |
| **Приложения** | Кто идёт через VPN, кто напрямую |
| **Настройки** | Темы, интервал пинга, отображение протокола/IP, Telegram автора |
| **Виджет** | Вкл/Выкл, пинг, обновление, лучший сервер |
| **Шторка** | Плитка **V** — быстрый тумблер; долгое нажатие открывает приложение |

---

## Быстрый старт

1. Установите APK из [Releases](https://github.com/VegsPro/VpEgs/releases/latest).
2. Откройте **Подписки** → **+** → вставьте ссылку подписки → **Добавить**.
3. Выдайте разрешение VPN, когда система спросит.
4. На **Главной** нажмите большую кнопку **ON** или выберите сервер на вкладке **Серверы**.
5. (Опционально) Добавьте **виджет** на рабочий стол и плитку **V** в шторку.

### Обход приложений

1. **Приложения** → при необходимости выключите «Глобальный режим».
2. Для нужных приложений: **VPN** = через туннель, **Напрямую** = мимо VPN.
3. После изменения списка дождитесь переподключения (или выключите/включите VPN).

> Некоторые приложения (Госуслуги, банки) видят сам факт системного VPN, даже если их трафик идёт напрямую — это ограничение Android, не бага клиента.

---

## Ссылки

| | |
|---|---|
| Telegram автора | [**@mstrblock**](https://t.me/mstrblock) |
| Репозиторий | [github.com/VegsPro/VpEgs](https://github.com/VegsPro/VpEgs) |
| Issues | [Сообщить о проблеме](https://github.com/VegsPro/VpEgs/issues) |

---

## Сборка из исходников

```bash
git clone https://github.com/VegsPro/VpEgs.git
cd VpEgs
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

Требования: JDK 17+, Android SDK (minSdk 29 / Android 10+).

Package ID: `com.vpegs.ms`

---

## Важно

- Приложение предназначено для **личного** использования со **своими** конфигурациями/подписками.
- Не собирает аналитику и не требует регистрации.
- Работа VPN зависит от качества вашей подписки и сети.

---

<p align="center">
  Сделано для удобной работы с современными прокси‑протоколами · <a href="https://t.me/mstrblock">@mstrblock</a>
</p>
