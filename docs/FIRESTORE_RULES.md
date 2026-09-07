# ScreenRest Firestore 보안 규칙

## 적용 방법

루트의 `firestore.rules`가 현재 전체 규칙입니다. Firebase Console의 **Firestore Database > 규칙**에 파일 전체를 붙여 넣어 게시하거나 Firebase CLI에서 아래 명령을 실행합니다.

```bash
firebase deploy --only firestore:rules
```

## 사용하는 컬렉션

- `screenrest_pairing_codes/{pairingCode}`: 자녀 연결 코드
- `screenrest_children/{childDeviceId}`: 자녀와 연결 부모 정보
- `screenrest_children/{childDeviceId}/unlock_requests/{requestId}`: 추가 시간 요청
- `screenrest_children/{childDeviceId}/remote_commands/{commandId}`: 부모 승인 명령
- `screenrest_children/{childDeviceId}/push_tokens/{tokenId}`: FCM 수신 대상

## FCM 토큰 규칙

- 토큰 문서는 로그인한 현재 기기만 만들거나 갱신할 수 있습니다.
- 자녀 토큰은 해당 자녀 문서의 `childUid`와 로그인 UID가 일치해야 합니다.
- 부모 토큰은 해당 자녀 문서의 `parentUids`에 로그인 UID가 있어야 합니다.
- 앱 클라이언트는 토큰 문서를 읽거나 목록 조회할 수 없습니다.
- 자신의 토큰 문서만 삭제할 수 있습니다. 연결 해제 직후의 정리도 가능하도록 삭제에는 현재 연결 여부를 요구하지 않습니다.
- Cloud Functions의 Admin SDK는 보안 규칙을 우회하지만, 함수에서 자녀 UID와 연결 부모 UID를 다시 확인합니다.

## 최소 권한 규칙

- 자녀 문서의 `childUid`와 `childDeviceId`는 생성 후 변경할 수 없습니다.
- 연결된 부모는 자녀 문서 전체를 덮어쓸 수 없습니다. 본인 프로필 갱신, 본인 연결 해제, 원자적 연결 확정에 필요한 필드만 변경할 수 있습니다.
- 승인 요청은 자녀가 생성하며, 부모는 Pending 요청의 승인·거절 필드만 변경할 수 있습니다.
- 원격 명령은 연결된 부모만 정해진 스키마로 생성할 수 있고 클라이언트 수정은 허용하지 않습니다.
- 문서 ID, UID, 역할, 상태, 숫자 범위, 문자열 크기와 변경 필드 목록을 규칙에서 검증합니다.
- 연결 부모와 프로필 배열은 자녀 한 명당 최대 10개로 제한합니다.

규칙을 게시하기 전에 자녀 코드 생성 → 부모 연결 → 요청 → 승인/거절 → 연결 해제 → 7일 경과 데이터 삭제 흐름을 테스트 프로젝트에서 검증해야 합니다. 운영 프로젝트에는 검증되지 않은 규칙을 바로 게시하지 않습니다.

## 무료 데이터 정리 정책

- 자녀 기기는 7일이 지난 요청과 원격 명령을 기존 정리 작업에서 삭제합니다.
- FCM 토큰은 같은 앱 설치에서 토큰이 바뀌어도 같은 문서 ID로 덮어씁니다.
- 연결이 끊긴 UID나 FCM이 무효라고 응답한 토큰은 알림 함수가 발견하는 즉시 삭제합니다.
- 별도의 Firestore TTL 정책은 사용하지 않습니다.

전체 규칙 원문은 [`firestore.rules`](../firestore.rules)를 기준으로 관리합니다.
