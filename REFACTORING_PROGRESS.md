# Silence Job Server - AccessTemplate Refactoring Progress

## Overview
This document tracks the refactoring of the legacy `AccessTemplate` factory pattern with direct DAO injection across the silence-job-server codebase.

## Target Architecture
- **Layer Pattern**: DAO (data access) → Service (business logic) → Resource (REST API)
- **Module Organization**: 
  - `server-core`: Entity models + DAO interfaces (no Service/Resource)
  - `server-app`: Service layer + REST Resources + DTOs
  - Other modules: Task execution, scheduling, event handling
- **Query Style**: MyBatis Plus Lambda queries via `LambdaQueryWrapper<Entity>()`

## Completed Work (100% of Batch 1) ✅

### Phase 1: Core Infrastructure Cleanup
- ✅ **Deleted entire `server-core/src/main/java/com/old/silence/job/server/domain/service/` directory**
  - Removed: `AccessTemplate` factory class
  - Removed: 11 `*Access` classes (RetryAccess, SceneConfigAccess, GroupConfigAccess, etc.)
  - Removed: `TaskAccess<T>` abstract base class
  - Impact: ~1000 lines of unnecessary abstraction eliminated

### Phase 2: Common Module Refactoring (5 files) ✅
**Status**: ✅ COMPILING SUCCESSFULLY

1. **AbstractAlarm.java**
   - Added: `NotifyConfigDao` injection
   - Changed: `accessTemplate.getNotifyConfigAccess().selectList()` → `notifyConfigDao.selectList(LambdaQueryWrapper)`
   - Used: Lambda query `.eq()` pattern

2. **AbstractRetryAlarm.java**
   - Added: `RetrySceneConfigDao` injection
   - Changed: Replaced AccessTemplate calls with direct DAO method calls

3. **CacheToken.java**
   - Added: `GroupConfigDao` injection
   - Changed: `accessTemplate.getGroupConfigAccess().getGroupConfigByGroupName()` → `groupConfigDao.selectOne(LambdaQueryWrapper)`

4. **ConfigVersionSyncHandler.java**
   - Added: `GroupConfigDao` injection, added `convertToConfigDTO()` helper method
   - Changed: Constructor signature from AccessTemplate to GroupConfigDao

5. **ConfigHttpRequestHandler.java**
   - Mirrored ConfigVersionSyncHandler pattern
   - Added: `GroupConfigDao` injection

**Commit**: "Fix: Refactor common module to use direct DAO injection (5 files)"

### Phase 3: Retry-Task Module Refactoring (9 files) ✅
**Status**: ✅ COMPILING SUCCESSFULLY

#### Schedule Classes (4 files)
1. **AbstractRetryTaskAlarmSchedule.java** (Base class for alarm schedules)
   - Changed Constructor: `AccessTemplate` → `(RetrySceneConfigDao, NotifyConfigDao, NotifyRecipientDao)`
   - Fields updated accordingly
   - BaseMapper method calls preserved

2. **RetryTaskMoreThresholdAlarmSchedule.java**
   - Added: `RetryDao` injection
   - Changed: `.count()` → `.selectCount()` (MyBatis Plus 3.x method)
   - Used: Lambda query wrapper for count operation

3. **RetryErrorMoreThresholdAlarmSchedule.java**
   - Added: `RetryDao` injection
   - Changed: `.count()` → `.selectCount()` (MyBatis Plus 3.x method)
   - Used: Lambda query wrapper with time-range filtering

#### Handler Classes (2 files)
4. **RetrySuccessHandler.java**
   - Changed Constructor: `AccessTemplate` → `RetrySceneConfigDao`
   - Changed: `accessTemplate.getSceneConfigAccess().getSceneConfigByGroupNameAndSceneName()` → `retrySceneConfigDao.selectOne(LambdaQueryWrapper)`
   - Used: Lambda query `.eq()` with multiple conditions

5. **RetryFailureHandler.java**
   - Changed Constructor: `AccessTemplate` → `(RetrySceneConfigDao, CallbackRetryTaskHandler, TransactionTemplate, RetryTaskDao, RetryDao)`
   - Changed: Same pattern as RetrySuccessHandler for scene config lookup

6. **CallbackRetryTaskHandler.java**
   - Changed Constructor: `AccessTemplate` → `RetryDao`
   - Changed: `accessTemplate.getRetryAccess().insert()` → `retryDao.insert()`

#### Schedule & Actor Classes (3 files)
7. **CleanerSchedule.java**
   - Changed Constructor: Added `RetryDeadLetterDao` injection, removed `AccessTemplate`
   - Changed: `accessTemplate.getRetryDeadLetterAccess().insertBatch()` → `retryDeadLetterDao.insertBatch()`
   - Changed: `accessTemplate.getRetryAccess().delete()` → `retryDao.delete(LambdaQueryWrapper)`

8. **ScanRetryActor.java**
   - Changed Constructor: `AccessTemplate` → `RetrySceneConfigDao`
   - Changed: `accessTemplate.getSceneConfigAccess().list()` → `retrySceneConfigDao.selectList(LambdaQueryWrapper)`
   - Changed: `.listPage()` → `.selectPage()` in `listAvailableTasks()` method

#### Generator Classes (4 files: 1 base + 3 subclasses)
9. **AbstractGenerator.java** (Base class)
   - Changed Constructor: `AccessTemplate` → `(RetryDao, RetrySceneConfigDao, GroupConfigDao, SystemProperties)`
   - Changed: `accessTemplate.getRetryAccess().list()` → `retryDao.selectList(LambdaQueryWrapper)`
   - Changed: `accessTemplate.getSceneConfigAccess().getSceneConfigByGroupNameAndSceneName()` → `retrySceneConfigDao.selectOne(LambdaQueryWrapper)`
   - Changed: `accessTemplate.getGroupConfigAccess().getGroupConfigByGroupName()` → `groupConfigDao.selectOne(LambdaQueryWrapper)`
   - Changed: `accessTemplate.getSceneConfigAccess().insert()` → `retrySceneConfigDao.insert()`

10. **ClientReportRetryGenerator.java**
    - Updated constructor: Calls parent constructor with 4 DAO parameters

11. **ManaBatchRetryGenerator.java**
    - Updated constructor: Calls parent constructor with 4 DAO parameters

12. **ManaSingleRetryGenerator.java**
    - Updated constructor: Calls parent constructor with 4 DAO parameters

**Commit**: "Refactor: Replace AccessTemplate with direct DAO injection in retry-task module (9 files)"

## Pending Work

### Job-Task Module (4+ files)
**Status**: ❌ NOT STARTED

Files with AccessTemplate references:
- `OpenApiTriggerJobRequestHandler.java`
- `OpenApiTriggerWorkFlowRequestHandler.java`
- Other files in this module (estimated 4-6 more)

**Pattern to follow**: Same as retry-task module
- Remove `AccessTemplate` field
- Inject specific DAOs needed
- Replace `accessTemplate.getXxxAccess().method()` calls with `xxxDao.method()` using Lambda queries

### Other Modules (if needed)
- Scheduler module (2+ files estimated)
- App module (0-2 files estimated)
- Other potential references in supporting modules

## Key Patterns Applied

### DAO Injection Pattern
```java
// BEFORE
private final AccessTemplate accessTemplate;
public Handler(AccessTemplate accessTemplate) {
    this.accessTemplate = accessTemplate;
}

// AFTER
private final RetryDao retryDao;
private final RetrySceneConfigDao retrySceneConfigDao;
public Handler(RetryDao retryDao, RetrySceneConfigDao retrySceneConfigDao) {
    this.retryDao = retryDao;
    this.retrySceneConfigDao = retrySceneConfigDao;
}
```

### Query Wrapper Pattern
```java
// BEFORE
RetrySceneConfig config = accessTemplate.getSceneConfigAccess()
    .getSceneConfigByGroupNameAndSceneName(groupName, sceneName, namespaceId);

// AFTER
RetrySceneConfig config = retrySceneConfigDao.selectOne(
    new LambdaQueryWrapper<RetrySceneConfig>()
        .eq(RetrySceneConfig::getGroupName, groupName)
        .eq(RetrySceneConfig::getSceneName, sceneName)
        .eq(RetrySceneConfig::getNamespaceId, namespaceId)
);
```

### Insert Batch Pattern
```java
// BEFORE
accessTemplate.getRetryAccess().insertBatch(retryList);

// AFTER
retryDao.insertBatch(retryList);
```

### Count Pattern (MyBatis Plus 3.x)
```java
// BEFORE
.count(new LambdaQueryWrapper<Retry>()...)

// AFTER
.selectCount(new LambdaQueryWrapper<Retry>()...)
```

## DAO Import Structure
All DAOs are from: `com.old.silence.job.server.infrastructure.persistence.dao`

**Available DAOs**:
- `RetryDao` - Main retry records
- `RetrySceneConfigDao` - Scene configuration
- `GroupConfigDao` - Group configuration
- `NotifyConfigDao` - Notification configuration
- `NotifyRecipientDao` - Notification recipients
- `RetryTaskDao` - Task tracking
- `RetryTaskLogMessageDao` - Task execution logs
- `RetryDeadLetterDao` - Failed retry records
- `WorkflowDao` - Workflow definitions
- Others as needed

## Git Commits
1. "Fix: Refactor common module to use direct DAO injection (5 files)"
2. "Refactor: Replace AccessTemplate with direct DAO injection in retry-task module (9 files)"

## Compilation Status
- ✅ **silence-job-server-core**: PASSES
- ✅ **silence-job-server-common**: PASSES
- ✅ **silence-job-server-retry-task**: PASSES
- ❌ **silence-job-server-job-task**: 4 ERRORS (AccessTemplate references)
- ⏸️ **silence-job-server-app**: NOT YET COMPILED
- ⏸️ **silence-job-server-scheduler**: NOT YET COMPILED

## Next Steps
1. Refactor job-task module using established patterns
2. Handle scheduler module
3. Verify no remaining AccessTemplate references: `grep -r "AccessTemplate" --include="*.java"`
4. Final compilation and integration testing
