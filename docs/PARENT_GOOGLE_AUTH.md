# 부모 Google 계정 및 복구 배포

## 구현된 흐름

- 자녀 기기: Firebase 익명 인증 유지
- 부모 기기: Credential Manager의 Google 로그인 사용
- 기존 익명 부모: Google 자격 증명을 익명 계정에 연결하여 기존 Firebase UID 보존
- 재설치 부모: 같은 Google 계정으로 로그인하면 `parentUids`에 현재 UID가 포함된 자녀 문서를 다시 조회
- 다른 역할로 변경: 연결된 기기를 먼저 해제해야 하며, 자녀 역할로 변경하면 새 익명 인증으로 전환
- 자녀 코드 등록: 부모 Google 로그인 완료 후에만 가능

## Firebase Console에서 필요한 작업

1. Firebase Console → Authentication → 로그인 방법에서 Google 제공업체를 활성화합니다.
2. 프로젝트 설정 → Android 앱 `com.manisykh.screenrest`에 업로드/디버그 SHA-1과 SHA-256을 등록합니다.
3. Google Auth Platform에서 OAuth 동의 화면의 앱 이름, 지원 이메일과 대상 사용자를 설정합니다.
4. 웹 애플리케이션 유형 OAuth 클라이언트가 생성되었는지 확인합니다.
5. Firebase에서 새 `google-services.json`을 다운로드해 로컬 `app/google-services.json`을 교체합니다.
6. 생성 리소스에 `default_web_client_id`가 포함되는지 확인합니다.

`google-services.json`은 Git에 커밋하지 않습니다.

## 계정 및 클라우드 데이터 삭제

앱의 부모 관리 화면에서 관리 PIN을 확인한 뒤 `deleteCurrentUserData` Callable Function을 호출합니다.

함수는 다음 정보를 삭제합니다.

- 현재 UID가 소유한 자녀 문서와 모든 하위 요청·명령·FCM 토큰
- 다른 자녀 문서에 남은 현재 부모 UID와 연결 프로필
- 현재 UID가 만든/사용한 연결 코드
- Firebase Authentication 사용자

이 함수는 `enforceAppCheck: true`이므로 Debug App Check 토큰 등록 또는 Play Integrity가 정상이어야 합니다.

배포:

```cmd
set FUNCTIONS_DISCOVERY_TIMEOUT=30
firebase deploy --only functions:deleteCurrentUserData
```

기존 FCM 함수와 함께 전체 배포하려면 `firebase deploy --only functions`를 사용합니다.

## 복구 검증

1. 기존 부모 앱에서 Google 계정으로 로그인합니다.
2. 자녀 연결이 그대로 유지되는지 확인합니다.
3. 다른 테스트 기기 또는 앱 데이터 초기화 후 부모 모드와 PIN을 설정합니다.
4. 같은 Google 계정으로 로그인합니다.
5. 연결 코드 입력 없이 기존 자녀 목록이 복원되는지 확인합니다.
6. 요청 알림, 승인과 거절이 복원된 부모 기기에서 정상 동작하는지 확인합니다.

## 제한 사항

- 자녀 앱을 삭제하면 익명 UID가 바뀌므로 기존 자녀 문서의 소유권을 그대로 되찾을 수 없습니다. 새 자녀 코드로 재등록하고 부모 화면에서 이전 자녀 항목을 해제해야 합니다.
- 외부 계정 삭제 요청용 공개 HTTPS 페이지는 별도로 게시해야 합니다.
