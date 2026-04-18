# React + Vite Frontend Project

## ⚠️ Rule: Every commit must compile

Every commit on this repo must build cleanly. Before committing:

```bash
npm run build
```

(which runs `tsc` + `vite build`) must pass with no errors. Do **not** commit intermediate TypeScript-broken state hoping the next commit fixes it — combine the work into one commit instead. This keeps `main` always deployable and makes branch-switching safe.

## Build & Run

- **Install**: `npm install`
- **Dev**: `npm run dev` (runs on localhost:5173 by default)
- **Build**: `npm run build`
- **Unit tests** (Vitest, jsdom): `npm test` or `npm run test:ui`
- **E2E tests** (Playwright, headless Chrome): `npm run test:e2e` — requires the full stack running (`docker compose up -d` from repo root). Tests live in `frontend/e2e/`. They use system Chrome via `channel: 'chrome'` so no bundled browser download is needed.
- **Lint**: `npm run lint`
- **Format**: `npm run lint:fix`
- **Preview**: `npm run preview`

## Project Structure

```
src/
  components/       # React components (atomic design)
  pages/           # Page-level components
  hooks/           # Custom React hooks
  utils/           # Utility functions
  services/        # API clients
  styles/          # Global styles
  types/           # TypeScript type definitions
  App.tsx          # Root component
  main.tsx         # Entry point
public/            # Static assets
vite.config.ts     # Vite configuration
tsconfig.json      # TypeScript configuration
```

## Key Technologies

- React 19+
- Vite (build tool)
- TypeScript
- Tailwind CSS (or CSS-in-JS)
- Vitest for unit tests
- React Router for navigation
- WebSocket client for real-time communication

## Code Style

- Indentation: 2 spaces
- Format: Prettier (automated)
- Linting: ESLint
- TypeScript strict mode enabled
- Component names: PascalCase
- File names: kebab-case for components, camelCase for utils

## Testing

- Unit tests with Vitest
- React Testing Library for component tests
- Write tests for business logic and user interactions
- Mock API calls with MSW or custom mocks

## Development Workflow

1. Create feature branch: `git checkout -b feature/name`
2. Start dev server: `npm run dev`
3. Write components and tests
4. Run linting: `npm run lint:fix`
5. Build to verify: `npm run build`
6. Commit and create PR

## Environment Variables

- Create `.env.local` for local overrides (gitignored)
- `.env` for defaults that can be checked in
- Never commit `.env.local` or credentials

## WebSocket Communication

- Client connects to backend WebSocket endpoint (typically `/ws`)
- Uses STOMP protocol for structured messaging
- Subscribe to topics for real-time updates: `/topic/channel-name`
- Send messages to server: `/app/endpoint`
- Custom hooks (e.g., `useWebSocket`) handle connection lifecycle
- Reconnection logic with exponential backoff
- Message parsing and state updates trigger re-renders

## Performance Notes

- Code-split large routes with React.lazy()
- Optimize images before committing
- Use React DevTools Profiler for performance debugging
- Keep component tree flat where possible
- WebSocket message handlers should update state efficiently
- Avoid unnecessary re-renders on high-frequency WebSocket messages
