## kmp 과제

### 과제 시작 전 첫 commit 을 남겨주세요.

### 제출 조건

1. 과제 repo clone 후 개인 github 에 private repository 생성 후 Settings > Collaborators and teams > Manage access > Add People > dy128.kim@wisebirds.com, kimjieun@wisebirds.com
   , bruno.park@wisebirds.com 계정을 추가해주세요
2. **제출 방법**: 과제 시작 시간부터 4시간 이내 필수로 push 해주세요.
3. 아래 구현 요구사항 외에는 제약 사항 없이 자유롭게 구현해주시면 됩니다.
4. **중요!!! 평가 기준**: 기능 구현 완성도, 최적화 및 설계 품질을 중점 검토 예정입니다.
   - 데이터 양, 종류가 많을 수 있다는 가정하에 최적화를 진행해주세요.
   - 특히, 캠페인 페이지의 필터 key와 value 의 개수는 일정하지 않고 항상 변할 수 있습니다.
   - 4시간에 비해 구현할 양이 많지만 구현양보다 코드 퀄리티를 더 중요하게 검토할 예정입니다.
5. **유의 사항** :
   채용 과제 내용 및 진행 방식에 대한 일체의 정보 및 자료은 회사의 사전 서면 동의 없이 제3자에게 공개, 누설 또는 제공하는 것을 엄격히 금지하고 있습니다.

### 구현 내용

광고 Admin Dashboard 앱 개발 (Kotlin Multiplatform, Compose Multiplatform)

**사전 과제 안내**

- 라이브러리 사용은 제한이 없습니다. (상태관리, 네비게이션, DI, UI 라이브러리 등 자유롭게 선택해주세요.)
- 현재 project 는 JetBrains KMP wizard 로 생성한 Compose Multiplatform(Android / iOS) 프로젝트이며, API 호출용 `ktor`, `kotlinx.serialization` 과 bcrypt salt 생성용 `cryptography-random` 만 추가되어 있습니다. (비밀번호 해시는 `utils/BCrypt.kt`)

  ```
  kmp-assignment
      - androidApp (Android 진입점)
      - iosApp (iOS 진입점, Xcode 프로젝트)
      - shared
          - src/commonMain/kotlin/com/wisebirds/assignment
              App.kt (빈 홈 화면)
              - utils
                  ApiClient.kt
                  BCrypt.kt (비밀번호 해시, 수정 불필요)
                  BlowfishTables.kt (BCrypt 상수표, 수정 불필요)
  ```

- 화면 기획서를 보고 자유롭게 개발해주세요. (디자인 보다 기능 위주로 개발해주세요.)
- api는 shared/src/commonMain/kotlin/com/wisebirds/assignment/utils/ApiClient.kt 사용해주시면 됩니다.
- UI 는 commonMain 에서 Compose Multiplatform 으로 작성해주세요.
- Android / iOS 중 1개 이상에서 실행 확인 후 제출해주세요.

**실행 방법** (Android Studio + Kotlin Multiplatform plugin, iOS 는 Xcode. Gradle daemon 은 JDK 21 을 사용하며 없으면 첫 빌드 때 자동으로 내려받습니다)

```
# Android
./gradlew :androidApp:installDebug

# iOS
iosApp/iosApp.xcodeproj 를 Xcode 로 열어 실행
```

### 과제 완료 후

- README.md 에 이름을 적어주시고 과제에서 이 부분을 이렇게 해냈다 자랑해주세요.
- 실행 확인한 플랫폼(Android / iOS)을 적어주세요.

**화면 기획서**

- 모바일 화면 디자인: [mobile_assignment Figma](https://www.figma.com/design/xZx1pmU04qPKuv98oHoEKz/mobile_assignment?node-id=0-1) — 화면별 번호와 Description 이 아래 명세와 같습니다.
- 참고: [web 과제 Figma (기능 원본)](https://www.figma.com/design/6rjgLOPHhI48AekufTc7dY/frontend_assignment?node-id=0-1&t=R2GCfWkkp2aTIVL3-1)

#### 1. 공통 레이아웃

| # | 항목 | 명세 |
|---|---|---|
| ① | 로고 | Wisebirds 표기 |
| ② | 권한 설정 | 어드민, 매니저, 뷰어 선택하여 권한을 변경할 수 있다 |
| ③ | 내 정보 | 탭 시 bottom sheet 로 이름, 메일(아이디), 회사 이름 표기 |
| ④ | 하단 탭 (메뉴 권한) | 캠페인 - 어드민, 매니저, 뷰어 / 사용자 - 어드민. 매니저, 뷰어 선택 시 사용자 탭은 제거 & 접근 불가 (사용자 화면에 있었으면 캠페인으로 이동) |

#### 2. 캠페인 관리

| # | 항목 | 명세 |
|---|---|---|
| ① | 타이틀 | 페이지 타이틀 "캠페인 관리" 표기 |
| ② | 필터 | 캠페인 주기, 캠페인 목적으로 필터링. key 마다 한 줄, 값은 가로 스크롤 칩. 리스트 헤더로 붙어 함께 스크롤 |
|   |   | - 각 필터 항목 클릭 시 해당 값으로 목록 필터링, 복수 선택 가능 |
|   |   | - "전체" 선택 시 필터 해제 |
|   |   | - 활성화된 필터는 강조 표시 |
|   |   | - 필터 key 와 value 의 개수는 일정하지 않고 항상 변할 수 있음 |
| ③ | 캠페인 카드 | 아래 9개 값 전부 표기 |
| ④ | 페이지네이션 | 무한 스크롤. 목록 끝에 닿으면 다음 25개 로드 |

③ 캠페인 카드 값

| 값 | 규칙 |
|---|---|
| 상태 | 상태 값에 따라 토글버튼으로 표시, 어드민, 매니저 권한은 토글 활성화 (수정 가능) / 뷰어 권한은 해당 값 disabled 처리(수정 불가) |
| 캠페인명 | 서버에서 내려주는 텍스트 그대로 표기 |
| 캠페인 주기 | DAILY, WEEKLY, MONTHLY 값 표기 |
| 캠페인 목적 | 서버에서 내려주는 코드 값을 텍스트 매치하여 표기 — LEAD: 리드, VIDEO_VIEWS: 동영상 조회, WEBSITE_CONVERSIONS: 웹사이트 전환, SALES: 판매, BRAND: 브랜드 인지도 및 도달 범위, APP_INSTALLATION: 앱설치, WEBSITE_TRAFFIC: 웹사이트 트래픽 |
| 노출수 | 0~십만 단위의 정수, 3번째 자리마다 , 표기 |
| 클릭수 | 0~천 단위의 정수, 3번째 자리마다 , 표기 |
| CTR | float, 소수 세번째 자리에서 반올림하여 % 값으로 표기 |
| 동영상조회수 | 0~백 단위의 정수 |
| VTR | float, 소수 세번째 자리에서 반올림하여 % 값으로 표기 |

#### 3. 사용자 관리 (어드민 전용)

| 항목 | 명세 |
|---|---|
| 타이틀 | 페이지 타이틀 "사용자 관리" 표기 |
| 목록 행 | 아이디, 이름, 마지막 로그인 일시 (yyyy-mm-dd HH:mm:ss). 행 탭 → 사용자 수정 |
| 페이지네이션 | 무한 스크롤, 요청당 25개 |
| 사용자 생성 진입 | (＋) 버튼 |
| 생성·수정 화면 형태 | 별도 화면. bottom sheet 도 허용 |
| 사용자 생성 — 아이디(이메일) | 9-50자 이내. 1) 미입력: 아이디(이메일)을 입력하세요. 2) 형식 오류: 올바른 이메일 주소를 입력하세요. 3) 중복: 이미 사용중인 이메일입니다. |
| 사용자 생성 — 비밀번호 | 영문, 숫자, 특수문자 조합 8~15자. 마스킹 처리 (해제 가능). 1) 미입력: 비밀번호를 입력하세요. 2) 사용불가: 8~15자 영문, 숫자, 특수문자를 사용하세요. |
| 사용자 생성 — 비밀번호 확인 | 1) 미입력: 비밀번호를 입력하세요. 2) 불일치: 비밀번호가 일치하지 않습니다. |
| 사용자 생성 — 이름 | 한글, 영문 1~16자. 1) 미입력: 이름을 입력하세요. 2) 형식 오류: 이름을 올바르게 입력하세요. |
| 사용자 생성 — 버튼 | 취소: 화면 닫힘. 생성: 정상 입력 시 생성 후 닫힘, 비정상 시 밸리데이션 표기 |
| 사용자 수정 — 아이디 | 텍스트로 표기 (수정 불가) |
| 사용자 수정 — 이름 | 1) 미입력: 이름을 입력하세요. 2) 형식 오류: 이름을 올바르게 입력하세요. (숫자, 특수문자, 공백 입력 불가) |
| 사용자 수정 — 버튼 | 취소: 화면 닫힘. 저장: 정상 입력 시 수정 후 닫힘, 비정상 시 밸리데이션 표기 |

#### 4. 에러

- API(서버) 에러 발생 시 에러 다이얼로그 표기

**Reference**

[kotlin multiplatform doc](https://kotlinlang.org/docs/multiplatform/get-started.html)

[compose multiplatform doc](https://kotlinlang.org/docs/multiplatform/compose-multiplatform.html)

[ktor client doc](https://ktor.io/docs/client-create-new-application.html)
