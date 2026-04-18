# Implementation Approach

## Feature Development Workflow

1. **Plan** — Design the feature with requirements and clarifications
2. **Backend Tests** — Write test cases for APIs and business logic (TDD)
3. **Backend Code** — Implement to make tests pass
4. **Backend Verify** — Confirm all tests pass, feature works end-to-end
5. **Backend Format & Commit** — Apply format, add all files, commit with message
6. **Frontend Tests** — Write component/integration tests
7. **Frontend Code** — Implement UI to make tests pass
8. **Frontend Verify** — Confirm all tests pass, feature works end-to-end
9. **Frontend Format & Commit** — Apply format, add all files, commit with message
10. **Add git tag after finishing functionality**

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

### 4. Backend Verify
- Run all backend tests: `cd backend && ./gradlew test` (should be 100% passing)
- Verify backend builds: `cd backend && ./gradlew build`
- Test APIs manually if needed

### 5. Backend Format & Commit
- Format code: `./gradlew spotlessApply`
- Stage files: `git add backend/`
- Commit with clear message: `git commit -m "feat(backend): feature name description"`

### 6. Frontend Tests
- Write unit tests for components and hooks
- Write tests for service calls (with mocks)
- Use Vitest and React Testing Library
- Tests should fail initially
- Run tests to confirm they fail: `npm test -- --run`

### 7. Frontend Code
- Implement React components to make tests pass
- Use TypeScript for type safety
- Follow existing patterns and style

### 8. Frontend Verify
- Run all frontend tests: `npm test -- --run` (should be 100% passing)
- Verify frontend builds: `npm run build`
- Test feature end-to-end in browser
- Verify no regressions in other features

### 9. Frontend Format & Commit
- Format code: `npm run lint:fix`
- Stage files: `git add frontend/`
- Commit with clear message: `git commit -m "feat(frontend): feature name description"`

### 10. Add git tag after finishing functionality
- After both backend and frontend are complete and committed
- Create git tag: `git tag -a v1.feature-name -m "Feature #N: Description"`
- Example: `git tag -a v1.user-auth -m "Feature #1: User Registration & Authentication"`

## Current Status

**Feature #1: User Registration & Authentication**
- Tasks 1-9: ✅ Complete
- Tasks 10-12: In progress

**Remaining Work:**
- Task 10: LoginPage component
- Task 11: AuthGuard and App.tsx routing
- Task 12: Full integration testing
