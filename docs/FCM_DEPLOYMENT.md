# ScreenRest FCM 배포 및 검증

## 구현 범위

FCM은 Firestore 데이터를 대신 전달하지 않고 **동기화를 즉시 깨우는 신호**로만 사용합니다.

- 자녀가 요청 생성 → 연결된 부모 기기에 FCM 전송
- 부모가 승인 또는 거절 → 해당 자녀 기기에 FCM 전송
- 승인·거절 완료 → 연결된 다른 부모 기기에도 정리 신호를 보내 기존 Pending 알림 제거
- 수신 기기 → Firestore에서 실제 요청과 연결 관계를 다시 조회
- 기존 Firestore listener와 15분 WorkManager → FCM 누락·지연 시 예비 경로로 유지
- 같은 요청 알림 → 앱의 기존 이벤트 토큰으로 중복 표시 방지
- 무효 토큰 또는 연결이 끊긴 UID의 토큰 → 함수 실행 중 자동 삭제
- 부모가 사용 현황 동기화 요청 → 자녀 기기에 보통 우선순위 데이터 신호를 보내 새 측정을 예약
- 자녀가 새 사용량 문서를 저장한 다음 요청 문서에 완료를 기록; 오프라인이면 정기 작업으로 복구

## Firebase에서 한 번 해야 할 일

Cloud Functions 배포에는 Blaze 요금제가 필요합니다. FCM 메시지 자체는 별도 유료 기능이 아니지만 Functions와 Firestore 사용량은 프로젝트 할당량 및 요금 정책의 적용을 받습니다.

1. Firebase Console에서 현재 Android 앱이 등록된 프로젝트를 선택합니다.
2. 프로젝트 설정 > Cloud Messaging에서 Firebase Cloud Messaging API가 활성화되어 있는지 확인합니다.
3. 프로젝트를 Blaze로 변경합니다.
4. Google Cloud 결제 메뉴에서 월 예산 알림을 설정합니다.
5. Firebase CLI가 없다면 설치하고 로그인합니다.

```bash
npm install -g firebase-tools
firebase login
firebase use --add
```

`firebase use --add`에서 ScreenRest Android 앱이 등록된 Firebase 프로젝트를 선택합니다. 저장소에는 프로젝트 ID를 고정한 `.firebaserc`를 넣지 않았습니다.

## 배포 순서

먼저 토큰 저장 권한을 배포한 뒤 함수를 배포합니다.

```bash
firebase deploy --only firestore:rules
cd functions
npm install
cd ..
firebase deploy --only functions
```

Windows에서 최초 소스 분석이 10초를 넘기면 다음처럼 분석 제한을 30초로 늘려 다시 배포합니다.

```cmd
set FUNCTIONS_DISCOVERY_TIMEOUT=30
firebase deploy --only functions
```

현재 배포 대상 함수는 다음과 같습니다.

- `processCreatedUnlockRequestPush`: 새 요청
- `processUpdatedUnlockRequestPush`: 같은 요청의 재전송과 승인·거절
- `deleteCurrentUserData`: 인증된 현재 사용자의 계정·연결·하위 클라우드 데이터 삭제
- `processCreatedImmediateBlockPush` / `processUpdatedImmediateBlockPush`: 즉시 차단 변경
- `processCreatedChildUsageRefreshPush` / `processUpdatedChildUsageRefreshPush`: 자녀 사용 현황 새로고침 요청

사용 현황 새로고침만 추가 배포할 때는 **먼저 Firestore 규칙을 게시**한 뒤 다음 명령을 사용합니다.

```cmd
firebase.cmd deploy --only firestore:rules --project screenrest
set FUNCTIONS_DISCOVERY_TIMEOUT=30
firebase.cmd deploy --only "functions:processCreatedChildUsageRefreshPush,functions:processUpdatedChildUsageRefreshPush" --project screenrest
```

새 앱은 부모·자녀 기기 양쪽에 설치해야 합니다. 요청은 자녀별 `usage_refresh/current` 문서 하나를 덮어쓰며, 1분 안의 재요청은 기존 요청을 재사용합니다. 자녀가 응답하지 않으면 2분 후 부모 화면에 연결 대기, 30분 후 요청 만료가 표시됩니다. 사용량 본문은 기존 `usage_snapshots/current` 문서 하나만 유지합니다.

승인 요청 함수는 생성과 갱신을 분리해 7일 후 문서 삭제에 호출되지 않으며, 요청 한 번 또는 결정 한 번당 실제 트리거 호출은 한 번입니다.

함수 리전은 Firestore 지연을 줄이기 위해 `asia-northeast3`로 설정했습니다. 실제 Firestore 데이터베이스 리전이 다른 경우 [`functions/index.js`](../functions/index.js)의 `region`을 같은 권역으로 바꾸는 것이 좋습니다.

## 실기기 검증 순서

1. 업데이트된 앱을 부모·자녀 기기에 모두 설치합니다.
2. 두 기기에서 알림 권한이 허용되어 있는지 확인합니다.
3. 부모·자녀 연결 후 Firestore에서 각 자녀 문서 아래 `push_tokens`에 부모와 자녀 문서가 생성되는지 확인합니다.
4. 부모 앱을 최근 앱 목록에서 제거한 뒤 자녀가 추가 시간을 요청합니다.
5. 부모 알림을 눌렀을 때 요청 목록이 열리고 실제 요청이 조회되는지 확인합니다.
6. 부모가 승인한 뒤 자녀 앱 프로세스가 종료된 상태에서도 승인 알림과 임시 허용이 반영되는지 확인합니다.
7. 거절도 같은 방식으로 확인합니다.
8. 부모 연결 해제 후 해당 부모에게 새 요청 알림이 오지 않는지 확인합니다.
9. 네트워크를 끊은 상태에서 요청한 뒤 다시 연결했을 때 listener 또는 WorkManager가 최종 상태를 복구하는지 확인합니다.

## 장애 판단

- `push_tokens` 문서가 없음: 앱 버전, 알림/Firebase 초기화, Firestore 규칙을 확인합니다.
- 토큰은 있으나 함수 로그가 없음: Functions 배포 프로젝트와 Android 앱의 Firebase 프로젝트가 같은지 확인합니다.
- 함수 로그에 권한 오류: Cloud Messaging API와 Functions 서비스 계정 권한을 확인합니다.
- 함수 성공이나 알림이 없음: Android 알림 권한 및 `부모 승인 요청` 채널 차단 여부를 확인합니다.
- FCM이 지연되어도 앱 실행 중 listener와 백그라운드 WorkManager가 최종 동기화를 수행합니다.

## 비용 안전장치

- 함수의 `maxInstances`를 10으로 제한했습니다.
- 요청 하나는 부모 알림 함수 1회, 승인·거절은 자녀 알림 함수 1회가 기본입니다.
- 동일 자녀의 부모 토큰은 최대 500개만 한 번에 조회합니다.
- 연결이 끊겼거나 무효인 토큰은 즉시 삭제해 불필요한 발송을 줄입니다.
- `maxInstances`는 과금 상한선이 아니므로 Google Cloud 예산 알림을 별도로 설정해야 합니다.
