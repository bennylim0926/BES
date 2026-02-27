---
description: Make frontend-only UI changes using existing API endpoints
---

# Update UI

## 1. Check Available Endpoints

Before making frontend changes, verify the backend endpoint exists:
- Check `BES-frontend/src/utils/api.js` and `adminApi.js` for existing API functions
- If the endpoint exists in the backend but not in `api.js`, add the fetch function first
- Reference Swagger UI at `/swagger-ui.html` for endpoint details

## 2. Add API Function (if needed)

In `BES-frontend/src/utils/api.js`, follow the existing pattern:
```javascript
export const myFunction = async (param) => {
  try {
    const res = await fetch(`${domain}/api/v1/resource/${param}`, {
      credentials: 'include'
    })
    return await res.json()
  } catch (e) {
    console.log(e)
  }
}
```

## 3. Update Component or View

- Components go in `src/components/`
- Full page views go in `src/views/`
- Use PrimeVue components where possible
- Follow existing TailwindCSS 4 patterns

## 4. Router (if new page)

Add route to `src/router/index.js`.

## 5. Test

Write or update Vitest test in `src/utils/__tests__/` for any new API functions.

```bash
cd BES-frontend && npm test
```
