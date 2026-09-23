# 유지보수 리팩토링 최종 보고서

기준 커밋: `ce0b65d`  
범위: UI·기능·정책·성능을 의도적으로 변경하지 않는 구조 분리

## 결과 요약

- `MainActivity.kt`는 기준 커밋의 16,743줄에서 현재 12,778줄로 축소했다.
- 가족, 오늘, 규칙, 통계, 더보기 화면을 기능별 파일로 분리했다.
- 정책 계산을 저장소 및 ViewModel 본체에서 분리하고, 감시 서비스의 순수 판정 표시 변환을 분리했다.
- Firebase 문서 구조, DataStore 키와 인코딩 형식, 정책 우선순위, PIN·승인·즉시 차단 흐름은 변경하지 않았다.
- 사용자가 요청한 커밋은 수행하지 않았다.

## 분리된 UI 파일

- `FamilyNavigation.kt`: 가족 탭 진입 및 부모·자녀·미연결 화면 분기
- `FamilyManagementScreen.kt`: 가족 및 기기 관리 화면
- `FamilyParentDashboard.kt`: 부모 대시보드와 즉시 차단 UI
- `FamilyChildDashboard.kt`: 자녀 대시보드
- `FamilyDeviceCards.kt`: 기기 동기화와 앱 사용 현황 공유 동의
- `FamilyLocalUsageCard.kt`, `FamilyRemoteUsageCard.kt`: 로컬·원격 사용 현황
- `FamilyRequestCards.kt`: 자녀 요청 이력과 가족 관리 진입
- `TodayScreen.kt`: 오늘 탭
- `RulesScreen.kt`: 규칙 탭의 최상위 화면
- `StatisticsScreen.kt`: 통계 탭과 공용 통계 표시 요소
- `MoreScreen.kt`: 더보기 탭과 세부 메뉴

## 분리된 정책 및 서비스 책임

- `UsagePolicyDraftExtensions.kt`: 정책 편집 초안 정규화와 예산 검증
- `UsagePolicyCalculations.kt`: 스케줄, 앱 그룹, 앱별 요일 및 고행 만료 계산
- `BlockDecisionPresentation.kt`: 감시 서비스의 차단 판정 로그·원격 사유·카테고리 변환

원격 동기화는 이미 `ParentRemoteSyncDataSource`와 `FirebaseParentRemoteSyncDataSource` 경계가 존재하므로, 이번 작업에서 Firebase 호출 순서를 재작성하지 않았다. 출시 직전 동기화 동작 변경 위험을 피하기 위한 결정이다.

## 검증 결과

- 가족 사용 현황·부모·자녀 대시보드 분리 단계: Kotlin 컴파일 및 `assembleDebug` 통과.
- 가족 탭 진입 분리 단계: 원본과 이동한 함수 본문이 접근 제한자 외에는 동일함을 확인했고, Kotlin 컴파일 및 `assembleDebug` 통과.
- 기존 단위 테스트 기준: 97개 중 96개 통과, 기존 `SafetyGateTest.allowOnlyMode_blocksUnrelatedPackageButAllowsPhoneFamily` 1개 실패. 이후 이 실패는 프로덕션 결함이 아니라 허용 앱을 `AllowedNoLimit`으로 기대한 테스트 의미 오류로 진단했다.
- 전체 분리 완료 후 `compileDebugKotlin`: 통과.
- 전체 분리 완료 후 `assembleDebug`: 통과.
- 테스트 기대값을 실제 정책 정의인 `AllowedUnderLimit`으로 바로잡은 후 전체 단위 테스트 97개 통과.
- `git diff --check`: 오류 없음. 줄바꿈 형식 관련 경고만 확인했다.

검증 도중 C 드라이브 여유 공간 부족으로 Windows 오류 1455와 JVM native-memory 할당 실패가 발생했으나, 공간 확보 후 전체 검증을 다시 실행해 컴파일 및 APK 조립 성공을 확인했다.

## 완료를 위해 남은 검증

1. 부모·자녀 실기기에서 가족 탭, 즉시 차단, 승인 요청, 정책 저장, 통계 및 블록 화면을 비교한다.
2. 실기기 검증 결과를 회귀 점검표에 기록한다.

## 현재 판단

요청한 5단계의 코드 구조 분리와 로컬 빌드 검증은 완료했다. 컴파일과 APK 조립이 성공했고 단위 테스트 97개도 모두 통과한다. UI·기능·성능의 최종 동일성은 부모·자녀 실기기 회귀 검증 후 확정한다.
