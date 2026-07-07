# GenericDataTable Component

## 📖 Overview

A fully-featured, reusable data table component built with `@tanstack/react-table` and Material-UI. 

### Features
- ✅ Column-based filtering (text and numeric)
- ✅ Multi-column sorting
- ✅ Sticky headers during scroll
- ✅ Pagination
- ✅ Responsive design
- ✅ Custom cell renderers
- ✅ Row actions
- ✅ Separated business logic in custom hook

---

## 📦 Files

```
frontend/src/
├── components/
│   └── GenericDataTable/
│       ├── GenericDataTable.jsx  # Main table component
│       └── index.js               # Export module
└── hooks/
    └── useTableState.js           # Table state management hook
```

---

## 🚀 Quick Start

### 1. Import

```jsx
import GenericDataTable from 'components/GenericDataTable';
import useTableState from 'hooks/useTableState';
```

### 2. Setup State

```jsx
const tableState = useTableState({
  initialPageSize: 10,
  defaultSort: { field: 'createdAt', direction: 'desc' }
});
```

### 3. Define Columns

```jsx
const columns = [
  {
    accessorKey: 'name',
    header: 'Name',
    enableSorting: true,
    enableColumnFilter: true,
    meta: { filterType: 'text' }
  },
  {
    accessorKey: 'price',
    header: 'Price',
    enableSorting: true,
    enableColumnFilter: true,
    meta: { filterType: 'number' },
    cell: ({ getValue }) => `$${getValue()}`
  }
];
```

### 4. Use Component

```jsx
<GenericDataTable
  columns={columns}
  data={data}
  totalCount={totalCount}
  isLoading={isLoading}
  tableState={tableState}
/>
```

---

## 📚 Full Documentation

See [GENERIC-TABLE-IMPLEMENTATION-GUIDE.md](../../../GENERIC-TABLE-IMPLEMENTATION-GUIDE.md) for:
- Complete API reference
- Advanced usage examples
- Migration guide from TbaDataTable
- Troubleshooting

---

## 🔗 Example

See [MedicalServicesListExample.jsx](../../pages/medical-services/MedicalServicesListExample.jsx) for a complete working example.

---

## 📄 License

Internal use only - TBA WAAD System
