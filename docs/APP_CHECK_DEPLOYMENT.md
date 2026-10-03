# Firebase App Check 출시 절차

앱 코드는 빌드 유형별 공급자를 사용합니다.

- `debug`: Firebase App Check Debug Provider
- `release`: Play Integrity Provider

## 1. 디버그 빌드 확인

1. 디버그 앱을 실행합니다.
2. Logcat에서 `DebugAppCheckProvider`가 출력한 디버그 토큰을 찾습니다.
3. Firebase Console > App Check > 앱 > 디버그 토큰 관리에 등록합니다.
4. Firestore와 Firebase Authentication 연결이 정상인지 확인합니다.
5. 개발 중 반복 재설치 테스트를 위해 `deleteCurrentUserData` 함수는 현재
   `enforceAppCheck: false`로 배포합니다. Firebase Authentication과 관리 PIN 검사는
   그대로 유지되며, 함수는 로그인한 `request.auth.uid` 본인의 데이터만 삭제합니다.

Firestore처럼 App Check 강제가 활성화된 다른 Firebase 제품을 테스트할 때는
디버그 토큰 등록이 여전히 필요할 수 있습니다. 계정 삭제 함수는 이 예외에 포함되지
않으므로 `auth: VALID`, `app: INVALID`인 디버그 앱에서도 인증된 계정 삭제 호출을
처리합니다.

## 2. 출시 앱 등록

1. Firebase Console > App Check에서 Android 앱을 선택합니다.
2. Play Integrity 공급자를 등록합니다.
3. Play Console의 앱 서명 인증서 SHA-256과 업로드 인증서 SHA-256을 Firebase Android 앱 설정에 모두 등록합니다.
4. 내부 테스트 트랙에 AAB를 올리고 Play Store를 통해 설치합니다.
5. App Check 측정 화면에서 유효한 요청이 들어오는지 확인합니다.

## 3. 강제 적용 순서

강제 적용을 먼저 켜면 기존 빌드와 콘솔에 등록되지 않은 디버그 빌드가 차단됩니다.

1. App Check 포함 빌드를 내부 테스트에 배포
2. 자녀 연결, 부모 요청, FCM 토큰 등록, 승인/거절 검증
3. 테스트 계정으로 계정·클라우드 데이터 삭제 함수 검증
4. 유효 요청 비율과 오류 로그 확인
5. 출시 전 `deleteCurrentUserData`의 `enforceAppCheck`를 `true`로 복구
6. Firestore 강제 적용 활성화
7. Firebase Authentication 강제 적용 활성화
8. 단계별로 다시 연결 테스트

문제가 발생하면 강제 적용을 끄고 원인을 수정합니다. 앱 코드의 App Check 공급자는 유지합니다.
