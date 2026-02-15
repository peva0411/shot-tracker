# Product Requirements Document: Basketball Shot Tracker

## 1. Executive Summary

**Product Name:** Basketball Shot Tracker  
**Version:** 1.0 (MVP)  
**Date:** February 2026  
**Status:** Initial Planning  

### Overview
An Android mobile application that uses computer vision to automatically track and count successful basketball shots. Users point their phone camera at a basketball hoop, and the app detects when shots are made, maintaining statistics over time.

### Vision
Start with a simple, focused MVP that delivers core value: automatic shot tracking using the camera. Build a foundation that can be extended with advanced features in future iterations.

---

## 2. Problem Statement

Basketball players who want to track their shooting practice currently rely on:
- Manual counting (disrupts practice flow, prone to errors)
- External observers/coaches (not always available)
- Paper notebooks (inconvenient, no analytics)

**The Core Problem:** There's no easy, automated way for individual players to track their shooting practice without interrupting their workflow.

---

## 3. Goals & Success Metrics

### Primary Goals
1. **Automate shot tracking** - Eliminate manual counting during practice
2. **Simple user experience** - Open app, point camera, shoot baskets
3. **Accurate detection** - Reliably detect made shots (target: 85%+ accuracy)

### Success Metrics (MVP)
- User can complete a session in < 30 seconds of setup time
- Shot detection accuracy > 80%
- App successfully tracks at least 10 consecutive shots without crashes
- User can view basic statistics after session

---

## 4. Target Users

### Primary User: Amateur Basketball Player
- **Age:** 12-35 years old
- **Context:** Practices alone or with friends
- **Goal:** Improve shooting skills through data-driven practice
- **Technical Comfort:** Comfortable with smartphone apps
- **Environment:** Outdoor courts, gym courts, driveway hoops

---

## 5. MVP Features (Phase 1)

### 5.1 Core Functionality

#### Feature 1: Camera-Based Shot Detection
**Description:** Use phone camera to detect when ball goes through hoop

**Requirements:**
- App displays live camera view of basketball hoop
- User positions camera to view the hoop
- App detects when ball passes through hoop
- Visual/audio feedback confirms detected shot
- Counter updates in real-time

**Acceptance Criteria:**
- Camera preview displays at 15+ fps
- Detection works in standard lighting conditions
- False positive rate < 15%
- Feedback occurs within 1 second of shot

#### Feature 2: Session Management
**Description:** Start, track, and end practice sessions

**Requirements:**
- "Start Session" button initiates tracking
- Display running counter: Makes / Total Attempts
- Manual buttons to add/subtract if detection fails
- "End Session" button stops tracking and saves data

**Acceptance Criteria:**
- Session starts in < 3 seconds
- Manual adjustments take effect immediately
- Session data persists after closing app

#### Feature 3: Basic Statistics
**Description:** Show simple shooting statistics

**Requirements:**
- Display for current session:
  - Total shots attempted
  - Shots made
  - Shooting percentage
  - Session duration
- List of past sessions with date, makes, percentage

**Acceptance Criteria:**
- Stats update in real-time during session
- Historical data persists indefinitely
- Can view last 10 sessions

### 5.2 Technical Requirements

#### Platform
- Android 8.0 (API Level 26) or higher
- Portrait and landscape orientation support
- Works on phones with rear camera

#### Performance
- App launches in < 3 seconds
- Camera preview latency < 100ms
- Detection processing < 500ms per frame
- Battery usage < 20% per 30-minute session

#### Storage
- Local SQLite database for session history
- < 50MB total app size
- Minimal data storage (< 1MB per 100 sessions)

---

## 6. Out of Scope (MVP)

The following features are explicitly **not included** in MVP to maintain focus:

### Deferred to Future Iterations
- Shot location tracking (corner, wing, free throw, etc.)
- Video recording of sessions
- Social features / sharing
- Multiple user profiles
- Advanced analytics (charts, trends, heat maps)
- Shot type classification (layup, three-pointer, etc.)
- Integration with wearables
- Cloud sync / backup
- Drill templates or guided workouts
- Audio coaching / feedback
- Comparison with professional benchmarks

---

## 7. User Experience (MVP)

### User Flow
1. **Launch App** → Main screen with "Start Session" button
2. **Start Session** → Camera view opens
3. **Position Camera** → User points camera at hoop (on-screen guide)
4. **Shoot Baskets** → App detects makes automatically
5. **Manual Adjust** → Tap +/- buttons if needed
6. **End Session** → Tap "End", view summary
7. **View History** → Navigate to history screen

### Key Screens
1. **Home Screen**
   - Large "Start Session" button
   - "View History" button
   - Today's stats summary (if any)

2. **Active Session Screen**
   - Camera viewfinder (majority of screen)
   - Shot counter overlay (e.g., "12 / 20 - 60%")
   - Manual +/- buttons (small, bottom corners)
   - "End Session" button

3. **Session Summary Screen**
   - Big percentage number
   - Makes / Attempts
   - Duration
   - "Save" or "Discard" options

4. **History Screen**
   - List of past sessions
   - Each item: Date, Makes/Attempts, Percentage
   - Tap to view details

---

## 8. Technical Approach

### Shot Detection Strategy (MVP)
Given the complexity of computer vision, we'll start with the **simplest viable approach**:

**Option 1: Motion Detection (Recommended for MVP)**
- Use Android Camera2 API
- Implement basic motion detection in hoop region
- Detect significant motion (ball passing through)
- Trigger on downward motion through hoop area
- Use basic image processing (frame differencing)

**Why this approach:**
- Simplest to implement
- Requires no ML models or heavy processing
- Good enough accuracy for MVP
- Fast iteration cycles

**Known Limitations:**
- May have false positives (birds, leaves, etc.)
- Requires stable camera position
- Works better with solid backgrounds
- Manual correction buttons compensate for errors

### Technology Stack
- **Language:** Kotlin
- **Camera:** CameraX or Camera2 API
- **Image Processing:** OpenCV Android SDK (lightweight)
- **Database:** Room (Android SQLite wrapper)
- **UI Framework:** Jetpack Compose or XML layouts
- **Architecture:** MVVM pattern

---

## 9. Design Principles

1. **Simplicity First** - Every feature must justify its existence
2. **Speed** - Fast to launch, fast to start tracking
3. **Forgiving** - Manual correction always available
4. **Focus** - Camera view is primary interface
5. **Reliable** - Prefer working 80% solution over perfect 100% that crashes

---

## 10. Risks & Mitigations

| Risk | Impact | Probability | Mitigation |
|------|--------|-------------|------------|
| Detection accuracy too low | High | Medium | Include manual correction; iterate on algorithm |
| Battery drain excessive | Medium | Medium | Optimize processing; add battery warnings |
| Diverse lighting conditions | High | High | Test in multiple environments; provide setup guidance |
| Camera positioning difficult | Medium | Medium | Add visual guides; save preferred position |
| App crashes during session | High | Low | Robust error handling; auto-save progress |

---

## 11. Success Criteria (MVP Launch)

### Must Have (Launch Blockers)
- [ ] App detects made shots with 70%+ accuracy in controlled conditions
- [ ] App runs for 30-minute session without crashing
- [ ] Session data persists reliably
- [ ] Works on 3+ different Android devices
- [ ] Manual correction buttons function correctly

### Should Have
- [ ] Detection accuracy 80%+ in outdoor daylight
- [ ] Battery usage < 25% per 30-minute session
- [ ] App size < 30MB

### Nice to Have
- [ ] Works in moderate low-light conditions
- [ ] Landscape mode support
- [ ] Session notes feature

---

## 12. Future Iterations (Post-MVP)

### Phase 2: Enhanced Tracking
- Shot location tracking (multiple zones)
- Streak tracking (consecutive makes)
- Practice goal setting (make 100 shots)
- Session types (free throw, three-point, etc.)

### Phase 3: Analytics & Insights
- Weekly/monthly statistics
- Progress charts and trends
- Shooting heat maps
- Personal best tracking

### Phase 4: Social & Motivation
- Share sessions with friends
- Leaderboards
- Challenges and achievements
- Video highlights of sessions

### Phase 5: Advanced Features
- Multiple camera angles
- Shot arc analysis
- Form feedback using pose estimation
- Integration with basketball training apps
- Coach/player accounts with shared stats

---

## 13. Development Timeline (Estimated)

### Phase 1 - MVP (8-10 weeks)
- **Week 1-2:** Project setup, UI mockups, tech stack finalization
- **Week 3-4:** Camera integration and basic shot detection
- **Week 5-6:** Session management and data persistence  
- **Week 7-8:** Statistics screens and history
- **Week 9-10:** Testing, bug fixes, polish

### Milestones
- **Week 4:** Working camera with basic detection demo
- **Week 6:** Complete session flow functional
- **Week 8:** All MVP features complete
- **Week 10:** MVP ready for alpha testing

---

## 14. Open Questions

1. **Detection Method:** Should we explore ML models early, or stick with motion detection for MVP?
2. **Camera Position:** Should we support tripod/stand, or assume handheld?
3. **Outdoor vs Indoor:** Should MVP focus on one environment first?
4. **Hoop Types:** Do we need to support different hoop types (regulation, portable, mini)?
5. **Missed Shots:** Should MVP track misses, or just makes?

---

## 15. Dependencies & Assumptions

### Dependencies
- Android device with working camera
- Adequate lighting conditions
- Relatively stable camera position
- Basketball hoop visible in frame

### Assumptions
- Users are willing to manually correct occasional errors
- Basic motion detection is sufficient for MVP
- Users practice in conditions with reasonable lighting
- 70-80% accuracy is acceptable for initial version
- Users understand this is a tracking tool, not a coaching tool

---

## 16. Appendix

### Similar Products (Research)
- HomeCourt (iOS basketball app with shot tracking)
- ShotTracker (wearable sensor system)
- Various basketball counting apps (manual)

### Key Differentiators
- **Automated** - No wearables or manual input needed
- **Simple** - Single-purpose, easy to use
- **Free** - No subscription model (MVP)
- **Android-first** - Underserved compared to iOS

### Target File Size
- APK: < 30MB
- With dependencies (OpenCV): ~25-30MB
- User data: < 100KB per 100 sessions

---

## Document History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | Feb 2026 | Initial | Created MVP PRD |

---

## Approval

_This PRD represents the MVP scope and will be updated as we iterate and learn from user feedback._

**Next Steps:**
1. Review and approve PRD
2. Create UI mockups
3. Set up development environment
4. Begin Week 1 development sprint