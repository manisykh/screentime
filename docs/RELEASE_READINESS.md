# 폰 쉼 출시 준비 현황

기준일: 2026-08-29

## 코드에 반영됨

- `compileSdk`와 `targetSdk` API 36 전환
- Play 모니터링 앱 선언: `isMonitoringTool=child_monitoring`
- 최초 실행 전 눈에 띄는 사용정보 수집·전송·백그라운드 동작 안내와 명시적 동의
- 동의 전 사용량 감시, 원격 Firestore listener, WorkManager 동기화, FCM 토큰 등록 차단
- 기본 PIN 제거 및 최초 단일 관리 PIN 설정
- PIN PBKDF2-HMAC-SHA256 해시 저장, 기존 평문 PIN 사용 시 자동 마이그레이션
- PIN 5회 오류 시 30초 입력 잠금
- Firestore 필드·역할별 최소 권한 규칙 작성
- Debug App Check / Release Play Integrity 공급자 분리
- Firebase Messaging 자동 초기화는 동의 전 비활성화
- 차단 시 미디어 정지는 미디어 키로 처리하고 과도한 알림 접근 서비스·필수 권한 제거
- 정확 알람 특별 권한을 제거하고 WorkManager + `setAndAllowWhileIdle()` 복구 경로로 전환
- Crashlytics 자동 수집 기본 비활성화, 모니터링 안내 동의 후에만 크래시·ANR 진단 활성화
- 출시 빌드 R8 난독화·리소스 축소와 Crashlytics 매핑 파일 업로드 준비
- `keystore.properties` 기반 로컬 서명 구성과 커밋 가능한 예제 파일 추가
- 부모 기기 Google 로그인과 기존 익명 부모 UID의 Google 계정 연결
- 동일 Google 계정 로그인 시 기존 자녀 연결 목록 복구
- 부모 Google 로그인 완료 전 신규 자녀 코드 등록 차단
- 앱 안에서 관리 PIN 확인 후 Firebase 계정과 클라우드 연결 데이터 삭제

## 출시를 막는 남은 항목

### 1. 인증과 재설치 복구의 남은 작업

부모 기기는 Google 계정으로 복구할 수 있고, 자녀 기기는 익명 인증을 유지하도록 구현했습니다. 다만 다음 운영 설정과 자녀 재등록 절차는 출시 전에 완료해야 합니다.

- Firebase Authentication의 Google 제공업체 활성화
- Android 앱의 디버그·업로드·Play App Signing SHA-1/SHA-256 등록
- 웹 OAuth 클라이언트가 포함된 최신 `google-services.json`으로 로컬 파일 교체
- `deleteCurrentUserData` Callable Function 배포와 App Check 검증
- 자녀 앱 삭제·데이터 초기화 시 새 익명 UID로 재등록하는 사용자 안내
- Firebase Console의 30일 익명 계정 자동 삭제는 자녀 재등록 UX 검증 전 비활성화
- 앱 밖에서도 사용할 수 있는 공개 HTTPS 계정 삭제 요청 페이지 게시

### 2. 개인정보처리방침과 Play Data safety

공개 HTTPS URL이 필요합니다. 최소한 다음 항목을 실제 동작과 일치하게 설명해야 합니다.

- 설치 앱 이름과 앱별 사용 시간
- 현재 실행 앱과 화면 상태
- 부모·자녀 연결 식별자
- 차단 사유, 사용/제한 시간, 승인 요청/결과
- FCM 토큰
- Firebase Authentication, Firestore, Cloud Functions, Cloud Messaging 사용
- 보관 기간, 삭제 요청, 보호자와 자녀의 권리, 문의처

개발자/사업자명, 문의 이메일, 공개 URL은 저장소에서 확인할 수 없어 아직 확정할 수 없습니다.

### 3. Play Console 선언

- 앱 액세스 방법과 심사용 테스트 절차
- 자녀 모니터링 앱 선언 및 스토어 설명의 지속 알림 고지
- Data safety
- 개인정보처리방침 URL
- Foreground service `specialUse` 선언과 사용 영상
- 대상 연령, Families 정책 해당 여부, 콘텐츠 등급

### 4. Firebase Console

- App Check Play Integrity 등록 후 내부 테스트에서 측정
- 검증 전 강제 적용 금지
- 강화된 `firestore.rules`를 테스트 프로젝트에서 먼저 게시
- 익명 계정 30일 자동 삭제 상태 확인
- API 키는 Android 앱 제한(패키지명+SHA-1/SHA-256) 적용
- 예산 알림과 Cloud Functions 최대 인스턴스 유지 확인

### 5. 출시 품질

- Android 11~16 실기기: 프로세스 종료, Doze, 재부팅, 오프라인, 시간대 변경
- 삼성/Pixel/태블릿: 전체 화면 블록, 멀티윈도우, 사진 선택, 인쇄, 전화/문자 필수 흐름
- 부모/자녀: 연결, 재연결, 중복 요청, 승인/거절, FCM 지연·실패, 연결 해제
- 30일 통계: 화면 꺼짐 제외, 자정 경계, 재부팅, 앱 업데이트, 오래된 데이터 병합
- 고행 1/2/3단계: 우회 불가, 종료 시각, Emergency Pass, UI 상태 자동 해제
- Play 내부 테스트에서 pre-launch report와 Android vitals 확인

## 권한 판단

`SCHEDULE_EXACT_ALARM`은 제거했습니다. 복구 및 자정 정리는 WorkManager와 비정확 idle 허용 알람을 함께 사용합니다. Doze에서는 실행 시각이 지연될 수 있으므로 실기기 검증에서 자정 정책 정리와 프로세스 복구 지연을 반드시 측정합니다.

## 빌드 검증

이 문서 작성 단계에서는 사용자의 요청에 따라 Gradle 컴파일, 설치, 장시간 테스트를 실행하지 않았습니다.

## 관련 문서

- Play Console 제출 준비: `docs/PLAY_CONSOLE_SUBMISSION.md`
- 실기기 검증표: `docs/RELEASE_TEST_MATRIX.md`
- 개인정보처리방침 초안: `docs/PRIVACY_POLICY_DRAFT_KO.md`
- App Check 배포: `docs/APP_CHECK_DEPLOYMENT.md`
- Firestore 규칙: `docs/FIRESTORE_RULES.md`
- FCM 배포: `docs/FCM_DEPLOYMENT.md`
- 부모 Google 인증·복구·삭제 배포: `docs/PARENT_GOOGLE_AUTH.md`
