# Implementation Approach

## Feature Development Workflow

1. **Plan** — Design the feature with requirements and clarifications
2. **Backend Tests** — Write test cases for APIs and business logic (TDD)
3. **Backend Code** — Implement to make tests pass
4. **Frontend Tests** — Write component/integration tests
5. **Frontend Code** — Implement UI to make tests pass
6. **Verify** — Confirm all tests pass, feature works end-to-end
7. **Commit** — Git commit when feature is complete

## Per-Step Details

### 1. Plan
- Clarify requirements and edge cases
- Identify API endpoints needed
- Design database schema if required
- List components and pages needed
- Ask for clarifications when something is unclear

### 2. Backend Tests
- Write unit tests for services (business logic)
- Write integration tests for controllers (API endpoints)
- Use TDD: tests should fail initially
- Run tests to confirm they fail: `./gradlew test`

### 3. Backend Code
- Implement minimal code to make tests pass
- Follow existing code patterns and style
- Use package-private visibility by default
- Format code: `./gradlew spotlessApply`

### 4. Frontend Tests
- Write unit tests for components and hooks
- Write tests for service calls (with mocks)
- Use Vitest and React Testing Library
- Tests should fail initially

### 5. Frontend Code
- Implement React components to make tests pass
- Use TypeScript for type safety
- Follow existing patterns and style
- Format code: `npm run lint:fix`

### 6. Verify
- Run all backend tests: `cd backend && ./gradlew test` (should be 100% passing)
- Run all frontend tests: `npm test -- --run` (should be 100% passing)
- Test feature end-to-end manually
- Verify no regressions in other features

### 7. Commit
- Stage files: `git add -A`
- Commit with clear message: `git commit -m "feat: feature name description"`
- Commit message format: 
  - First line: `feat:` or `fix:` followed by short description
  - Blank line
  - Bullet points describing what was added/changed

## Current Status

**Feature #1: User Registration & Authentication**
- Tasks 1-9: ✅ Complete
- Tasks 10-12: In progress

**Remaining Work:**
- Task 10: LoginPage component
- Task 11: AuthGuard and App.tsx routing
- Task 12: Full integration testing
