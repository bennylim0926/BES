# API Conventions

## URL Structure

```
/api/v1/{resource}           → collection
/api/v1/{resource}/{id}      → single item
/api/v1/{resource}/{action}  → RPC-style action
```

## HTTP Methods

| Method | Usage | Returns |
|--------|-------|---------|
| `GET` | Read data | JSON body |
| `POST` | Create or action | JSON body or 200 OK |
| `DELETE` | Remove | 200 OK |

## Request/Response Patterns

### Frontend → Backend

All frontend API calls must:
- Include `credentials: 'include'` (session cookies)
- Set `Content-Type: application/json` for POST/DELETE with body
- Set `Accept: application/json`

### Error Handling

- **404** → Return empty array `[]` (for list endpoints) or appropriate message
- **403** → Spring Security handles automatically (redirect to login or Forbidden)
- **500** → Log server-side, return generic error

## DTO Naming Conventions

| Pattern | Example | Usage |
|---------|---------|-------|
| `Add{Entity}Dto` | `AddEventDto` | Request body for creating |
| `Get{Entity}Dto` | `GetEventDto` | Response body for reading |
| `Update{Entity}Dto` | `UpdateGenreDto` | Request body for updating |
| `Delete{Entity}Dto` | `DeleteGenreDto` | Request body for deleting |

## Database Naming

- Table names: `snake_case` (auto-generated from entity class name)
- Column names: `snake_case` (auto-generated from field name by JPA)
- Primary keys: `{entity}_id` (e.g., `event_id`, `participant_id`)
- Foreign keys: `{referenced_entity}_id`

## New Migration File Naming

```
V{version}__{description}.sql
```
- Double underscore between version and description
- Use next sequential version number
- Description in `snake_case`: `V2__add_venue_to_event.sql`
