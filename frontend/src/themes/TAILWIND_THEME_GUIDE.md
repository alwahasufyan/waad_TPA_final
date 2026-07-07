# 🎨 Tailwind CSS-Inspired MUI Theme Guide

دليل استخدام الـ Theme المستوحى من Tailwind CSS في نظام TBA-WAAD.

---

## 📦 التثبيت والاستخدام

### 1. تطبيق الـ Theme على التطبيق

```jsx
// في ملف App.jsx أو index.jsx
import { ThemeProvider } from '@mui/material/styles';
import tailwindTheme from 'themes/tailwindTheme';

function App() {
  return (
    <ThemeProvider theme={tailwindTheme}>
      {/* Your app content */}
    </ThemeProvider>
  );
}
```

### 2. استخدام الألوان في الـ Components

```jsx
import { useTheme, alpha } from '@mui/material/styles';
import { Box, Button } from '@mui/material';
import { tw } from 'themes/tailwindTheme';

function MyComponent() {
  const theme = useTheme();

  return (
    <Box
      sx={{
        // استخدام ألوان Tailwind مباشرة
        bgcolor: tw.colors.blue[50],
        borderColor: tw.colors.slate[200],
        
        // أو استخدام theme palette
        color: 'primary.main',
        bgcolor: alpha(theme.palette.primary.main, 0.1),
        
        // Tailwind shadows
        boxShadow: tw.shadow.lg,
        
        // Tailwind border radius
        borderRadius: tw.rounded.xl
      }}
    >
      <Button
        sx={{
          // Tailwind ring effect
          '&:focus': tw.ring(theme.palette.primary.main, 0.5)
        }}
      >
        Click Me
      </Button>
    </Box>
  );
}
```

---

## 🎨 لوحة الألوان (Color Palette)

### Primary - Tailwind Blue
```jsx
primary.lighter    // #eff6ff (blue-50)
primary.light      // #93c5fd (blue-300)
primary.main       // #2563eb (blue-600) ← الأساسي
primary.dark       // #1d4ed8 (blue-700)
primary.darker     // #1e3a8a (blue-900)
```

### Success - Tailwind Emerald
```jsx
success.lighter    // #ecfdf5 (emerald-50)
success.main       // #059669 (emerald-600)
success.darker     // #064e3b (emerald-900)
```

### Warning - Tailwind Amber
```jsx
warning.lighter    // #fffbeb (amber-50)
warning.main       // #f59e0b (amber-500)
warning.darker     // #78350f (amber-900)
```

### Error - Tailwind Red
```jsx
error.lighter      // #fef2f2 (red-50)
error.main         // #dc2626 (red-600)
error.darker       // #7f1d1d (red-900)
```

### Info - Tailwind Sky
```jsx
info.lighter       // #f0f9ff (sky-50)
info.main          // #0ea5e9 (sky-500)
info.darker        // #0c4a6e (sky-900)
```

### Neutral - Tailwind Slate
```jsx
tw.colors.slate[50]   // #f8fafc (خلفية فاتحة)
tw.colors.slate[200]  // #e2e8f0 (حدود)
tw.colors.slate[600]  // #475569 (نص ثانوي)
tw.colors.slate[900]  // #0f172a (نص أساسي)
```

---

## 📐 Typography (الخطوط)

### Font Sizes - Tailwind Scale

```jsx
h1: { fontSize: '2.25rem' }     // text-4xl
h2: { fontSize: '1.875rem' }    // text-3xl
h3: { fontSize: '1.5rem' }      // text-2xl
h4: { fontSize: '1.25rem' }     // text-xl
h5: { fontSize: '1.125rem' }    // text-lg
h6: { fontSize: '1rem' }        // text-base
body1: { fontSize: '1rem' }     // text-base
body2: { fontSize: '0.875rem' } // text-sm
caption: { fontSize: '0.75rem' }// text-xs
```

### استخدام في الكود:

```jsx
<Typography variant="h3" fontWeight={700}>
  عنوان رئيسي
</Typography>

<Typography variant="body2" color="text.secondary">
  نص ثانوي
</Typography>
```

---

## 🎭 Shadows (الظلال)

### Tailwind Shadow System

```jsx
// استخدام مباشر
sx={{ boxShadow: tw.shadow.sm }}  // ظل خفيف
sx={{ boxShadow: tw.shadow.md }}  // ظل متوسط
sx={{ boxShadow: tw.shadow.lg }}  // ظل كبير
sx={{ boxShadow: tw.shadow.xl }}  // ظل أكبر
sx={{ boxShadow: tw.shadow['2xl'] }} // ظل عملاق

// أو عبر theme
sx={{ boxShadow: 1 }} // shadow-sm
sx={{ boxShadow: 2 }} // shadow
sx={{ boxShadow: 3 }} // shadow-md
sx={{ boxShadow: 4 }} // shadow-lg
```

---

## 🔲 Border Radius (الحواف المستديرة)

### Tailwind Rounded System

```jsx
sx={{ borderRadius: tw.rounded.sm }}   // 4px  (rounded-sm)
sx={{ borderRadius: tw.rounded.md }}   // 6px  (rounded-md)
sx={{ borderRadius: tw.rounded.lg }}   // 8px  (rounded-lg)
sx={{ borderRadius: tw.rounded.xl }}   // 12px (rounded-xl)
sx={{ borderRadius: tw.rounded['2xl'] }} // 16px (rounded-2xl)
sx={{ borderRadius: tw.rounded.full }} // 9999px (rounded-full)
```

---

## 💍 Ring Effects (تأثيرات التركيز)

### Tailwind Ring Utility

```jsx
import { tw } from 'themes/tailwindTheme';
import { useTheme } from '@mui/material/styles';

const theme = useTheme();

<Button
  sx={{
    '&:focus': tw.ring(theme.palette.primary.main, 0.5)
  }}
>
  Focus Me
</Button>

// النتيجة: ring-2 ring-blue-500/50
```

---

## 🎯 أمثلة عملية

### Card Component - Stitch Style

```jsx
import { Card, CardContent, Typography, Box, Avatar, alpha } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { tw } from 'themes/tailwindTheme';
import LocalHospitalIcon from '@mui/icons-material/LocalHospital';

function StatCard({ title, value, color = 'primary' }) {
  const theme = useTheme();

  return (
    <Card
      sx={{
        borderRadius: tw.rounded.xl,
        boxShadow: tw.shadow.md,
        position: 'relative',
        overflow: 'hidden',
        '&:hover': {
          boxShadow: tw.shadow.lg
        }
      }}
    >
      {/* Left accent bar */}
      <Box
        sx={{
          position: 'absolute',
          left: 0,
          top: 0,
          bottom: 0,
          width: 6,
          bgcolor: `${color}.main`
        }}
      />

      <CardContent sx={{ p: 3, pl: 4 }}>
        <Avatar
          sx={{
            bgcolor: alpha(theme.palette[color].main, 0.1),
            color: `${color}.main`,
            width: 48,
            height: 48,
            mb: 2
          }}
        >
          <LocalHospitalIcon />
        </Avatar>

        <Typography variant="h3" fontWeight={700}>
          {value}
        </Typography>
        <Typography variant="body2" color="text.secondary">
          {title}
        </Typography>
      </CardContent>
    </Card>
  );
}
```

### Button Component - Tailwind Style

```jsx
import { Button } from '@mui/material';
import { tw } from 'themes/tailwindTheme';

<Button
  variant="contained"
  sx={{
    borderRadius: tw.rounded.lg,
    boxShadow: tw.shadow.sm,
    '&:hover': {
      boxShadow: tw.shadow.md
    }
  }}
>
  Submit
</Button>
```

### Table Component - Professional

```jsx
import { Table, TableHead, TableRow, TableCell, alpha } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { tw } from 'themes/tailwindTheme';

const theme = useTheme();

<Table>
  <TableHead
    sx={{
      bgcolor: alpha(theme.palette.action.hover, 0.3)
    }}
  >
    <TableRow>
      <TableCell sx={{ fontWeight: 600 }}>Column 1</TableCell>
      <TableCell sx={{ fontWeight: 600 }}>Column 2</TableCell>
    </TableRow>
  </TableHead>
  <TableBody>
    <TableRow
      hover
      sx={{
        cursor: 'pointer',
        '&:hover': {
          bgcolor: alpha(theme.palette.primary.main, 0.04)
        }
      }}
    >
      <TableCell>Data 1</TableCell>
      <TableCell>Data 2</TableCell>
    </TableRow>
  </TableBody>
</Table>
```

---

## 🔧 تخصيص الـ Theme

### إضافة ألوان مخصصة

```jsx
// في tailwindTheme.js
const customColors = {
  brand: {
    50: '#your-color',
    500: '#your-color',
    900: '#your-color'
  }
};

// ثم استخدامها
sx={{ bgcolor: customColors.brand[500] }}
```

### تعديل الـ Spacing

```jsx
// في tailwindTheme.js
spacing: 4, // 1 unit = 4px (Tailwind default)

// الاستخدام
sx={{ p: 3 }} // padding: 12px (3 * 4px)
sx={{ m: 4 }} // margin: 16px (4 * 4px)
```

---

## 📚 المراجع السريعة

### Spacing Scale (Tailwind)
```
0  → 0px
1  → 4px
2  → 8px
3  → 12px
4  → 16px
6  → 24px
8  → 32px
12 → 48px
```

### Font Weights
```
300 → light
400 → normal
500 → medium
600 → semibold
700 → bold
```

### Breakpoints
```
xs: 0px
sm: 600px
md: 900px
lg: 1200px
xl: 1536px
```

---

## 🚀 أفضل الممارسات

1. **استخدم `alpha()` للشفافية**:
   ```jsx
   bgcolor: alpha(theme.palette.primary.main, 0.1)
   ```

2. **استخدم `tw.shadow` بدلاً من أرقام الظلال**:
   ```jsx
   boxShadow: tw.shadow.lg // ✅
   boxShadow: 4             // ❌
   ```

3. **استخدم `useTheme()` للوصول للألوان**:
   ```jsx
   const theme = useTheme();
   color: theme.palette.primary.main
   ```

4. **استخدم الـ semantic colors**:
   ```jsx
   color="primary"   // ✅ للأزرار الأساسية
   color="success"   // ✅ للنجاح
   color="error"     // ✅ للأخطاء
   ```

---

## 🎨 مقارنة مع Tailwind CSS

| Tailwind Class | MUI Equivalent |
|----------------|----------------|
| `bg-blue-50` | `sx={{ bgcolor: tw.colors.blue[50] }}` |
| `text-slate-900` | `sx={{ color: tw.colors.slate[900] }}` |
| `rounded-xl` | `sx={{ borderRadius: tw.rounded.xl }}` |
| `shadow-lg` | `sx={{ boxShadow: tw.shadow.lg }}` |
| `ring-2 ring-blue-500` | `sx={{ ...tw.ring(theme.palette.primary.main) }}` |
| `p-4` | `sx={{ p: 4 }}` (16px) |
| `text-2xl` | `<Typography variant="h3">` |

---

## 📞 الدعم والمساعدة

للمزيد من الأمثلة والاستخدامات، راجع:
- [Material-UI Documentation](https://mui.com/)
- [Tailwind CSS Colors](https://tailwindcss.com/docs/customizing-colors)
- [System Design Tokens](./DESIGN_TOKENS.md)

---

**تم التحديث:** 2026-01-03  
**الإصدار:** 1.0.0
