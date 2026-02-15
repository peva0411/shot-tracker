# Architecture Overview

## Pattern: MVVM (Model-View-ViewModel)

The Shot Tracker app follows the MVVM architecture pattern with clean architecture principles.

### Layers

#### 1. UI Layer (`ui/`)
- **Responsibility:** Display data and handle user interactions
- **Technology:** Jetpack Compose
- **Components:**
  - Composable functions (Views)
  - Screen-level composables
  - UI state classes
  - Theme definitions

#### 2. ViewModel Layer
- **Responsibility:** Manage UI state and handle business logic coordination
- **Technology:** Android ViewModel + StateFlow
- **Components:**
  - ViewModels for each screen
  - UI state holders
  - Event handlers

#### 3. Domain Layer (`domain/`)
- **Responsibility:** Business logic and data abstractions
- **Components:**
  - **Models:** Data classes representing business entities
  - **Use Cases:** Single-responsibility business operations
  - **Repository Interfaces:** Abstractions for data access

#### 4. Data Layer (`data/`)
- **Responsibility:** Data persistence and retrieval
- **Components:**
  - **Local:** Room database entities, DAOs
  - **Repository Implementations:** Concrete implementations of repository interfaces

#### 5. Camera Layer (`camera/`)
- **Responsibility:** Camera operations and shot detection
- **Components:**
  - **Detector:** Shot detection algorithms
  - **Processor:** Image processing with OpenCV

### Data Flow

```
User Interaction
    ↓
Composable (View)
    ↓
ViewModel
    ↓
Use Case
    ↓
Repository
    ↓
Data Source (Room/Camera)
    ↓
Repository
    ↓
Use Case
    ↓
ViewModel (StateFlow)
    ↓
Composable (View)
    ↓
UI Update
```

### Dependency Injection

- **Framework:** Hilt (Dagger)
- **Scope:** Application, Activity, ViewModel
- **Modules:** Will be created as needed for repositories, use cases, and utilities

### Navigation

- **Framework:** Jetpack Compose Navigation
- **Pattern:** Single Activity with multiple composable destinations
- **Routes:**
  - `home` - Home screen
  - `session` - Active session screen
  - `summary/{sessionId}` - Session summary
  - `history` - History screen

## Design Principles

1. **Separation of Concerns:** Each layer has a single, well-defined responsibility
2. **Dependency Inversion:** High-level modules don't depend on low-level modules
3. **Unidirectional Data Flow:** Data flows down, events flow up
4. **Single Source of Truth:** ViewModel holds the UI state
5. **Immutability:** UI state is immutable (use data classes with val)

## Testing Strategy

### Unit Tests
- Use Cases (business logic)
- ViewModels (state management)
- Repository implementations
- Utility functions

### Integration Tests
- Room database operations
- Repository + DAO interactions

### UI Tests
- Compose UI testing
- Navigation flows
- User interactions

## Future Considerations

- **Cloud Sync:** Add remote data source when implementing cloud features
- **ML Models:** Add ML layer when implementing advanced detection
- **Analytics:** Add analytics layer when implementing tracking
