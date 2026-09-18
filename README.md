# MiniRedis

Java로 Redis의 핵심 동작을 직접 구현하며 저장소, 네트워크, 만료, 영속성을 공부하는 작은 프로젝트다.

실제 Redis 전체를 복제하거나 운영 환경에서 사용하는 것이 목표는 아니다. 지원 범위를 작게 유지하면서 구현한 기능을 테스트로 검증하는 데 초점을 둔다.

## 기술 스택

- Java 21
- Gradle
- TCP Socket
- JUnit 5
- AssertJ

## 현재 지원 범위

### 서버와 프로토콜

- TCP 서버
- 고정 크기 Thread Pool을 이용한 동시 클라이언트 처리
- RESP Array 요청
- 일반 텍스트 명령
- UTF-8 Bulk String 바이트 길이 처리
- 잘못된 RESP 구조에 대한 오류 응답 후 연결 종료
- 명령 인자 오류와 알 수 없는 명령에는 연결 유지
- 명시적인 서버 종료와 자원 정리
- 실행 인자로 서버 포트와 AOF 경로 설정

### 명령어

- `PING`
- `SET key value`
- `GET key`
- `DEL key [key ...]`
- `EXISTS key [key ...]`
- `EXPIRE key seconds`
- `TTL key`
- `INCR key`
- `SETEX key seconds value`
- `INFO`
- `REPLCONF SYNC` 실험적 지원

명령어는 필요한 인자 개수를 검사한다. `SETEX`는 0 이하 또는 숫자가 아닌 TTL을 거부하고, `EXPIRE`의 0 이하 TTL은 기존 Key를 즉시 삭제한다. `INCR`는 signed 64-bit 정수 범위를 사용하며 기존 TTL을 유지한다.

### 저장소와 만료

- `ConcurrentHashMap` 기반 Key-Value 저장소
- 조회 시 만료 여부를 확인하는 Lazy Expiration
- 백그라운드에서 만료 Key를 정리하는 Active Expiration
- 일반 `SET`으로 덮어쓸 때 기존 TTL 제거
- `INFO`의 Key 수에서 만료된 Key 제외

### AOF 영속성

- `SET`, `SETEX`, `DEL` 변경 기록
- `EXPIRE` 만료 시각과 `INCR` 결과 기록
- 서버 재시작 시 데이터 복구
- `SETEX`의 원래 만료 시각 유지
- 공백, 한글, 빈 문자열 보존
- 레코드별 CRC32 체크섬 검증
- 전체 레코드 검증과 꼬리 복구가 성공한 뒤 저장소에 일괄 적용
- 기록 중 잘린 마지막 레코드 자동 제거 후 쓰기 재개
- 기록이 완료된 레코드가 손상된 경우 복구 실패로 보고
- 이전 `MR1` 및 공백 구분 AOF 읽기 지원

현재 AOF는 이 프로젝트 전용 `MR2` 형식이며 실제 Redis AOF와 호환되지 않는다.

### 복제

복제는 학습 목적의 실험적 기능이다. Replica 연결 이후 발생한 `SET`, `SETEX`, `DEL` 변경을 전달한다. 이 기능은 현재 수준에서 동결하며 MiniRedis의 완료 조건에 포함하지 않는다.

다음 기능은 지원하지 않는다.

- 연결 전 데이터의 전체 동기화
- 재연결과 자동 복구
- Replication offset과 ACK
- PSYNC와 부분 재동기화
- 장애 조치

## 테스트

현재 테스트는 다음 영역을 검증한다.

- 저장소의 SET, GET, DEL과 경계 조건
- Lazy/Active Expiration
- 명령어별 정상 입력과 잘못된 인자
- RESP 파싱, UTF-8, 손상된 요청
- 내장 클라이언트의 UTF-8 응답, CRLF 요청, 포트 옵션과 손상된 응답 처리
- AOF 기록, 삭제, 특수문자, TTL 복구, 체크섬, 잘린 꼬리 복구, 이전 형식 호환
- AOF 생성 실패, 종료 후 쓰기와 반복 종료 오류 경로
- 동시 `INCR` 이후 메모리와 AOF 복구 결과 일치
- TCP 서버 요청과 정상 종료
- 포트 충돌, 서버 시작 실패와 시작 대기 시간 초과
- 서버 시작 실패 시 프로세스와 백그라운드 작업 종료
- 다중 클라이언트 요청
- 기본 복제와 TTL 전달
- INFO와 서버 통계

전체 테스트 실행:

```shell
./gradlew test
```

Windows에서는 다음 명령을 사용할 수 있다.

```powershell
.\gradlew.bat test
```

테스트 실행 후 HTML 커버리지 리포트는 `build/reports/jacoco/test/html/index.html`에 생성된다.

```shell
./gradlew check
```

`check`는 핵심 코드의 라인 커버리지가 85% 미만이면 실패한다. 별도 프로세스 통합 테스트로 검증하는 `Main`만 커버리지 계산에서 제외한다.

## 실행

서버 실행:

```shell
./gradlew classes
java -cp build/classes/java/main com.example.miniredis.Main
```

기본 포트는 `6379`, 기본 AOF 경로는 `appendonly.aof`다. 필요한 경우 실행 인자로 변경할 수 있다.

```shell
java -cp build/classes/java/main com.example.miniredis.Main --port 6380 --aof mini.aof
```

지원하는 옵션:

- `--port <1-65535>`
- `--aof <file-path>`

잘못된 값, 누락된 값, 중복 옵션과 알 수 없는 옵션은 서버를 시작하지 않고 설정 오류로 처리한다.
현재 구현은 AOF 파일의 상위 디렉터리를 자동으로 만들지 않으므로, 디렉터리가 포함된 경로를 지정한다면 먼저 해당 디렉터리를 만들어야 한다.

별도 터미널에서 클라이언트 실행:

```shell
java -cp build/classes/java/main com.example.miniredis.MiniRedisClient
```

서버 포트를 변경했다면 클라이언트에도 같은 포트를 지정한다.

```shell
java -cp build/classes/java/main com.example.miniredis.MiniRedisClient --port 6380
```

사용 예:

```text
miniRedis> SET name gon
OK

miniRedis> GET name
gon

miniRedis> SETEX session 10 active
OK
```

내장 클라이언트는 간단한 대화형 확인 용도이며 일반 텍스트 명령을 전송한다. 공백이나 빈 문자열을 Key 또는 Value에 포함하려면 RESP Array를 지원하는 클라이언트를 사용해야 한다.

## 현재 제약

- AOF는 명령마다 버퍼를 비우지만 디스크 동기화(`fsync`)까지 보장하지 않는다.
- AOF rewrite나 압축이 없어 파일 크기가 계속 증가할 수 있다.
- 문자열 자료형과 문서에 나열된 명령만 지원한다.
- 내장 클라이언트는 로컬 서버(`localhost`) 연결만 지원한다.

## 프로젝트 구조

```text
src/main/java/com/example/miniredis
├── Main.java
├── MiniRedisClient.java
├── config
│   └── ServerConfig.java
├── cluster
│   └── ReplicaClient.java
├── network
│   ├── RedisServer.java
│   ├── RespHandler.java
│   └── command
├── persistence
│   └── AofManager.java
├── server
│   └── ServerStats.java
└── storage
    ├── DataValue.java
    ├── KeyValueStore.java
    └── scheduler
        └── ActiveExpirationScheduler.java
```

## 개발 원칙

1. 새로운 기능보다 현재 기능의 정확성과 테스트를 우선한다.
2. 버그를 수정할 때 재현 테스트를 먼저 추가한다.
3. 네트워크, 파일, 시간 경계 조건을 테스트한다.
4. 작은 프로젝트 범위를 벗어나는 기능은 추가하지 않는다.

## 프로젝트 완료 기준

다음 다섯 단계를 완료하면 MiniRedis의 학습용 1.0 범위가 끝난 것으로 본다.

1. [x] AOF 전체를 먼저 검증한 뒤 적용하여 복구 원자성을 보장한다.
2. [x] RESP 프로토콜 구조 오류가 발생하면 오류 응답 후 해당 연결을 종료한다.
3. [x] 포트와 AOF 경로를 실행 인자로 설정할 수 있도록 한다.
4. [x] 포트 충돌, AOF 입출력 실패, 서버 시작 실패 등 주요 오류 경로 테스트를 보강한다.
5. [x] `EXISTS`, `EXPIRE`, `TTL`, `INCR`, 여러 Key를 받는 `DEL`을 테스트와 함께 추가한다.

다섯 단계가 모두 완료되었다. 이후에는 새로운 대형 기능을 추가하지 않고 버그 수정과 문서 보완만 진행한다.

## 의도적으로 제외하는 범위

현재 단계에서는 다음 기능을 목표로 삼지 않는다.

- Transactions
- Pub/Sub
- RDB Snapshot
- Replica 재연결, 전체 동기화, PSYNC를 포함한 완전한 Redis Replication
- Memory Eviction Policy
- Redis Streams
- Cluster와 Sentinel

이 기능들은 프로젝트의 학습 범위를 크게 넓히므로 핵심 기능이 충분히 안정된 후 별도 목표로 검토한다.
