# Svita

**Your closet, no cloud.** · **Твоя шафа — без хмари.**

Svita is a free, open-source, offline-first wardrobe app for Android: catalog your clothes, get outfit picks from a dress-me engine that understands the weather and your comfort, track what you actually wear, and pack for trips — with **zero accounts, zero cloud, zero telemetry**.

> Status: early development (v0.1.0 in progress). The repository is being bootstrapped; the first runnable release will appear in Releases when ready.

## Features (v0.1)

- **Wardrobe catalog** — multi-photo items, categories, tags, seasons, price and notes
- **Rich customization** — editable categories, custom fields per category (text / number / enum / multi-select / color), tag manager (no fixed taxonomies)
- **Dress-me engine** — outfit picks by weather + your comfort (±15 °C slider), fully offline with a built-in climate-norms fallback; optional live weather via Open-Meteo (no API key)
- **Paper-doll avatar** — try outfits on a layered avatar, or view them as a flat-lay collage
- **Calendar & wear log** — plan outfits, log what you wore, «not worn in 60 days» nudges
- **Stats** — cost-per-wear, most/least worn
- **Packing lists** — trip packing from planned outfits
- **Data ownership** — full export/import (JSON + photos), CSV export. No lock-in, ever.

## Privacy

No account. No cloud. No ads. No subscriptions. No telemetry. The app works in airplane mode; the only optional network call is the weather forecast, and it degrades gracefully to offline climate norms.

## Build

Requirements: JDK 21, Android SDK (platform 35). 

```bash
./gradlew installDebug
```

UI languages: Ukrainian + English (follows system locale).

## Contributing

Issues and pull requests are welcome — see [CONTRIBUTING.md](CONTRIBUTING.md). Security reports: [SECURITY.md](SECURITY.md).

## License

[GPL-3.0](LICENSE)

---

## Українською

**Svita** («Світа») — безкоштовний офлайн-застосунок-гардероб з відкритим кодом для Android: каталог речей з фото, підбір образів за погодою і комфортом (слайдер ±15 °C), паперова лялька-аватар, щоденник носіння, cost-per-wear, пакувальні списки. Багата кастомізація: редаговані категорії та власні поля — жодних «фіксованих таксономій». **Без акаунтів, без хмари, без реклами, без телеметрії.** Повний експорт/імпорт даних. Ліцензія GPL-3.0.
