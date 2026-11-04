# Demo media for screenshots

`scripts/screenshots.sh` pushes the contents of this folder to the device's
`Pictures/` directory. **Each sub-folder becomes one album** in LiteGallery:

```
scripts/screenshot-media/
├── Travel/        ← album "Travel"
│   ├── 01.jpg
│   └── 02.mp4
├── Family/
└── Food/
```

Tips

- Use photos you are allowed to publish (your own non-private shots, or
  free-licensed images from Unsplash / Pexels). Never use real personal photos.
- 3–6 albums with 10–30 items each make the home and folder screens look full.
- Include a few short `.mp4` files so the video badges/durations appear.
- `adb push` keeps file modification times, so "sort by date" follows them.
  Adjust them with `touch -t 202605011200 file.jpg` if you want a specific order.
- Avoid single quotes (`'`) in file and folder names.

Everything in this folder except this README is ignored by git.
