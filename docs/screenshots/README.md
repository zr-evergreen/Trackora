# Screenshots

The main README links to six images from this folder. They are not in the repo
yet — drop them in with exactly these names and the tables will fill in:

| File | Screen | Suggested state |
| --- | --- | --- |
| `today.png` | Today | A few entries with different statuses, not the empty state |
| `add-edit.png` | Add / edit entry | A partly filled form, ideally showing a named custom field |
| `all-work.png` | All work | A list long enough to show the status filter doing something |
| `reports.png` | Reports | A month range with real totals |
| `date-picker.png` | Jalali date picker | The dialog open on a Jalali month |
| `rtl-persian.png` | Any screen in Persian | Shows the RTL layout — this is the one that proves the localisation is real |

Notes:

- Capture on a modern device or emulator (Pixel-class, API 34+) so the status
  bar and corners look current. The API 24 emulator is for compatibility
  testing, not for pictures.
- Use the same device and theme for all six so the set looks coherent.
- Fill them with plausible workshop data rather than "test test test" — the
  screenshots are the first thing anyone looks at, and placeholder text reads
  as an unfinished app.
- 1080px wide is plenty; anything larger just makes the clone slower.

To capture from a running emulator:

```bash
adb shell screencap -p /sdcard/today.png
adb pull /sdcard/today.png docs/screenshots/today.png
```
