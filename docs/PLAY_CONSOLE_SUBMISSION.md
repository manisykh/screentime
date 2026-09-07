# 폰 쉼 Play Console 제출 준비

기준일: 2026-08-29

이 문서는 현재 코드와 Google Play 공식 정책을 기준으로 만든 제출용 작업표입니다. Play Console에 실제로 입력하기 전에 앱 동작과 최종 개인정보처리방침을 다시 대조합니다.

## 1. 현재 코드에서 확인된 항목

- 패키지명: `com.manisykh.screenrest`
- `targetSdk`: 36
- 모니터링 분류: `child_monitoring`
- 포그라운드 서비스: `specialUse`
- 포그라운드 서비스 용도: 사용 시간 확인과 차단 정책 적용
- 지속 알림: 고유 앱 알림 아이콘과 `폰 쉼 · 사용 시간 관리 중` 제목
- 정확 알람 특별 권한: 사용하지 않음
- 알림 접근 권한: 사용하지 않음
- Firebase Analytics: 사용하지 않음
- Crashlytics: 최초 안내 동의 전 수집 비활성화

## 2. 스토어 등록정보 표현 원칙

폰 쉼은 반드시 **보호자가 자녀의 기기 사용 시간을 관리하는 자녀 보호 앱**으로만 설명합니다.

사용하지 않을 표현:

- 몰래 확인, 감시, 추적, 숨김
- 배우자나 성인 타인 감시
- 백그라운드 동작을 숨긴다는 설명

포함할 핵심 설명:

- 자녀 기기에서 사용 시간 관리 기능이 실행될 때 지속 알림이 표시됨
- 앱별 사용 시간과 차단 상태가 연결된 보호자에게 전달될 수 있음
- 사용정보 접근과 다른 앱 위 표시 권한이 필요한 이유
- 부모·자녀 연결은 사용자가 직접 시작하며 앱에서 연결을 해제할 수 있음

## 3. 앱 콘텐츠 제출 항목

### 개인정보처리방침

- 공개 HTTPS URL 준비
- 앱 안에서도 같은 정책을 열 수 있는 경로 제공
- 앱명, 운영자명, 문의 이메일, 수집·전송 항목, 목적, 보관 기간, 삭제 방법 기재
- 저장소 초안: `docs/PRIVACY_POLICY_DRAFT_KO.md`

### Data safety 초안

최종 답변은 Firebase SDK와 실제 전송 필드를 재검증하여 확정합니다.

- 앱 활동: 설치 앱 정보, 앱별 사용 시간, 현재 실행 앱과 정책 적용 상태
- 앱 정보 및 성능: Crashlytics 충돌 로그와 진단 정보
- 기기 또는 기타 식별자: Firebase UID, 자녀 기기 ID, FCM 토큰
- 기타 사용자 생성 콘텐츠/설정: 프로필명, 제한 정책, 추가 시간 요청과 승인 결과
- 전송 구간 암호화: 예
- 데이터 삭제 요청: 계정·클라우드 데이터 삭제 기능과 외부 URL 구현 후 `예`
- 광고 또는 데이터 판매: 아니요

기기 안에서만 처리되고 외부로 전송되지 않는 데이터와 Firebase로 전송되는 데이터를 구분해 답합니다.

### 모니터링 앱

- `isMonitoringTool=child_monitoring` 선언 확인
- 스토어 설명에서 자녀 모니터링 기능을 명시
- 실행 중 지속 알림과 고유 아이콘이 보이는 화면을 심사 영상에 포함
- 최초 안내와 동의 화면을 심사 영상에 포함

### Foreground service

Play Console의 앱 콘텐츠에서 `specialUse`를 선언하고 다음을 제출합니다.

- 기능: 자녀 기기의 현재 앱 사용을 확인하여 설정된 시간 제한과 차단 정책을 지연 없이 적용
- 지연 시 영향: 제한을 넘긴 앱이 잠시 더 실행되거나 차단 상태가 늦게 복구될 수 있음
- 중단 시 영향: 사용 시간 집계와 차단 정책 적용이 일시적으로 누락될 수 있음
- 영상: 기능 활성화 → 지속 알림 → 제한 도달 → 블록 화면 → 앱에서 기능 종료/설정 변경 순서

### 앱 액세스

심사자가 다음 흐름을 재현할 수 있는 한국어와 영어 안내를 준비합니다.

1. 최초 안내 동의
2. 단일 관리 PIN 생성
3. 사용정보 접근과 다른 앱 위 표시 권한 허용
4. 테스트 앱에 짧은 제한 설정
5. 제한 도달과 블록 화면 확인
6. 부모·자녀 테스트가 필요한 경우 두 기기 연결 절차와 테스트 코드 생성법 제공

## 4. 계정과 데이터 삭제

자녀 기기에는 익명 Firebase UID가 자동 생성되고 부모 기기는 Google 계정을 연결하므로, Google Play 계정 삭제 요구사항에 맞춰 다음 두 경로를 모두 제공해야 합니다.

- 앱 안: 부모 관리 → 계정 및 클라우드 데이터 삭제 — 구현됨
- 앱 밖: 공개 HTTPS 삭제 요청 페이지 — 게시 필요

앱 안 삭제는 인증 사용자, 소유 자녀 문서, 연결 정보, 요청, 원격 명령, FCM 토큰을 함께 정리합니다. 단순 연결 해제는 데이터 삭제가 아닙니다.

## 5. 출시 직전 확인

- `versionCode` 증가 및 `versionName` 확정
- Play App Signing 등록과 업로드 키 백업
- API 키 Android 앱 제한 및 노출된 키 제한/교체 상태 확인
- App Check는 내부 테스트에서 정상 요청 비율을 확인한 뒤 강제 적용
- 강화된 Firestore 규칙은 부모 연결·승인·거절·해제 전체 흐름을 검증한 뒤 게시
- 내부 테스트 AAB에서 R8/Crashlytics 매핑 파일 업로드 확인
- 심사 영상과 스토어 설명의 기능이 실제 앱과 일치하는지 확인

## 6. 공식 참고

- Google Play 모니터링 앱 정책: https://support.google.com/googleplay/android-developer/answer/9888380
- `isMonitoringTool` 안내: https://support.google.com/googleplay/android-developer/answer/12955211
- Foreground service 제출: https://support.google.com/googleplay/android-developer/answer/13392821
- 계정 삭제 요구사항: https://support.google.com/googleplay/android-developer/answer/13327111
- Data safety: https://support.google.com/googleplay/android-developer/answer/10787469
- Target API 요구사항: https://support.google.com/googleplay/android-developer/answer/11926878
