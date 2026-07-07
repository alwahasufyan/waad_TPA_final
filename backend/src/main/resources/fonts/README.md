# Arabic Fonts for PDF Reports

## Required Fonts

This directory should contain Arabic RTL fonts for professional PDF generation.

### Recommended: Amiri Font Family

1. **Download Amiri fonts from:**
   - Official: https://github.com/aliftype/amiri/releases
   - Google Fonts: https://fonts.google.com/specimen/Amiri

2. **Required files:**
   ```
   fonts/
   ├── Amiri-Regular.ttf
   ├── Amiri-Bold.ttf
   └── Amiri-Italic.ttf (optional)
   ```

3. **Installation:**
   - Download the font files from the links above
   - Place them in this directory: `backend/src/main/resources/fonts/`
   - Ensure file names match exactly (case-sensitive)

### Alternative: Noto Arabic Fonts

If Amiri is not available, use Noto Sans Arabic:

- Download from: https://fonts.google.com/noto/specimen/Noto+Sans+Arabic
- Files needed:
  ```
  fonts/
  ├── NotoSansArabic-Regular.ttf
  ├── NotoSansArabic-Bold.ttf
  ```

## Font Configuration

The `PdfFontConfig` class automatically:
1. Attempts to load Amiri fonts from this directory
2. Falls back to default Unicode fonts if Amiri is not found
3. Auto-detects Arabic vs Latin text
4. Applies RTL direction for Arabic text

## Fallback Behavior

If no custom fonts are provided:
- System will use BaseFont.HELVETICA (limited Arabic support)
- PDF will still generate but Arabic text may not render correctly
- **IMPORTANT:** Always include Amiri fonts for production use

## License Notes

- **Amiri**: SIL Open Font License (OFL) - Free for commercial use
- **Noto Sans Arabic**: SIL Open Font License (OFL) - Free for commercial use

Both fonts are safe to bundle with the application.

## Testing Font Installation

After adding font files, test with:

```java
// Will log font loading status
PdfFontConfig fontConfig = new PdfFontConfig();
```

Check logs for:
- ✅ "Arabic fonts (Amiri) loaded successfully"
- ⚠️ "Arabic fonts not found, using fallback"
