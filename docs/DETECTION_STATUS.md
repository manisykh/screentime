# Detection Status

`최근 감지`는 사용 시간 목록이 아니다. AccessibilityService가 마지막으로 foreground 전환을 감지했고, 차단 평가 대상이라고 판단한 일반 사용자 앱 1개를 보여주는 진단 영역이다.

## 표시 대상

- 사용자가 실제로 열어 조작하는 일반 앱
- 런처 목록에 보이는 사용자 앱
- Safe Mode OFF, Policy Enforcement ON 상태에서 차단 평가가 가능한 앱
- 앱별 제한, 그룹 제한, 전체 제한에 의해 `under limit` 또는 `would block` 판단을 받을 수 있는 앱

## 표시하지 않는 대상

- Screen Time Manager 앱 자체
- Android Settings
- Google Play Store
- Package Installer
- Permission Controller
- System UI, One UI Home, 런처
- 키보드, 입력기, 접근성 보조 앱
- 초기 설정, 설치 마법사, 백그라운드 시스템 서비스
- SafetyGate의 never-block whitelist에 포함된 앱

## 역할

- 접근성 이벤트가 실제로 들어오는지 확인
- SafetyGate가 평가 가능한 앱만 통과시키는지 확인
- 실제 차단 연결 전 `WouldBlock` 판단이 어떤 앱에서 발생하는지 확인

## 주의

- 최근 감지는 목록이 아니라 마지막 1개 상태다.
- 사용 시간 집계는 `오늘 사용`과 `정책 요약`에서 확인한다.
- 키보드나 시스템 앱이 최근 감지에 보이면 필터링 버그로 본다.
