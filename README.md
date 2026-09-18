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
- 잘못된 RESP 요청에 대한 오류 응답
- 명시적인 서버 종료와 자원 정리

### 명령어

- `PING`
- `SET key value`
- `GET key`
- `DEL key`
- `SETEX key seconds value`
- `INFO`
- `REPLCONF SYNC` 실험적 지원

명령어는 필요한 인자 개수를 검사하며, `SETEX`는 0 이하 또는 숫자가 아닌 TTL을 거부한다.

### 저장소와 만료

- `ConcurrentHashMap` 기반 Key-Value 저장소
- 조회 시 만료 여부를 확인하는 Lazy Expiration
- 백그라운드에서 만료 Key를 정리하는 Active Expiration
- 일반 `SET`으로 덮어쓸 때 기존 TTL 제거
- `INFO`의 Key 수에서 만료된 Key 제외

### AOF 영속성

- `SET`, `SETEX`, `DEL` 변경 기록
- 서버 재시작 시 데이터 복구
- `SETEX`의 원래 만료 시각 유지
- 공백, 한글, 빈 문자열 보존
- 손상되거나 알 수 없는 줄을 건너뛰고 나머지 데이터 복구
- 이전 버전의 공백 구분 AOF 읽기 지원

현재 AOF는 이 프로젝트 전용 `MR1` 형식이며 실제 Redis AOF와 호환되지 않는다.

### 복제

복제는 학습 목적의 실험적 기능이다. Replica 연결 이후 발생한 `SET`, `SETEX`, `DEL` 변경을 전달한다.

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
- AOF 기록, 삭제, 특수문자, TTL 복구, 이전 형식 호환
- TCP 서버 요청과 정상 종료
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

## 실행

서버 실행:

```shell
./gradlew classes
java -cp build/classes/java/main com.example.miniredis.Main
```

별도 터미널에서 클라이언트 실행:

```shell
java -cp build/classes/java/main com.example.miniredis.MiniRedisClient
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

## 프로젝트 구조

```text
src/main/java/com/example/miniredis
├── Main.java
├── MiniRedisClient.java
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

## 다음 우선순위

### 1. 테스트 안정성

- 고정 포트와 고정 대기 시간에 의존하는 네트워크 테스트 개선
- 반복 실행으로 간헐적 실패 확인
- 테스트 커버리지 리포트 도입 검토

### 2. 현재 기능 안정화

- AOF 마지막 줄이 기록 중 잘린 경우의 복구 정책 명확화
- Replica 연결 해제와 재연결 동작 테스트
- 서버 설정값을 실행 인자로 분리

### 3. 작은 명령 확장

안정화 이후 필요한 경우에만 다음 순서로 추가한다.

- `EXISTS`
- `EXPIRE`
- `TTL`
- `INCR`
- 여러 Key를 받는 `DEL`

각 기능은 정상 동작, 잘못된 입력, 만료 및 AOF 상호작용 테스트와 함께 추가한다.

## 의도적으로 제외하는 범위

현재 단계에서는 다음 기능을 목표로 삼지 않는다.

- Transactions
- Pub/Sub
- RDB Snapshot
- 완전한 Redis Replication
- Memory Eviction Policy
- Redis Streams
- Cluster와 Sentinel

이 기능들은 프로젝트의 학습 범위를 크게 넓히므로 핵심 기능이 충분히 안정된 후 별도 목표로 검토한다.
