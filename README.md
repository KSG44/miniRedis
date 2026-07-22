# MiniRedis

> Redis를 직접 구현하면서 내부 동작과 서버 개발을 공부하는 프로젝트

MiniRedis는 Java로 Redis의 핵심 기능을 하나씩 직접 구현해 보는 프로젝트다.

실제 Redis를 그대로 복제하는 것이 목표는 아니다.
TCP 통신부터 RESP 프로토콜, Command Pattern, TTL, AOF, Replication 등을 직접 구현하면서 Redis가 어떤 구조로 동작하는지 이해하는 데 초점을 두고 있다.

기능을 추가할 때마다 테스트 코드를 작성하고, 조금씩 실제 Redis와 비슷한 구조로 발전시키고 있다.

---

# 📌 Project Overview

이 프로젝트에서는 다음과 같은 내용을 직접 구현하고 있다.

- TCP 기반 서버
- RESP(Redis Serialization Protocol)
- Command Pattern
- TTL 및 만료 정책
- AOF(Append Only File)
- Replication
- 테스트 코드

Redis의 내부 구조를 이해하는 것뿐만 아니라 객체지향 설계와 네트워크 프로그래밍을 함께 연습하는 것이 목표다.

---

# 🛠 Tech Stack

- Java 21
- Gradle
- TCP Socket
- JUnit5
- AssertJ

---

# 📂 Project Structure

| Package | Description |
|---------|-------------|
| `cli` | MiniRedis CLI |
| `network` | TCP 서버와 RESP 처리 |
| `network.command` | Command Pattern |
| `storage` | Key-Value 저장소 및 TTL |
| `persistence` | AOF 저장 및 복구 |
| `cluster` | Replication |
| `server` | 서버 상태 정보(INFO) |
| `test` | 단위 테스트 |

```text
src
├── main
│   └── java
│       └── com.example.miniredis
│           ├── cli
│           │   └── MiniRedisClient
│           │
│           ├── cluster
│           │   └── ReplicaClient
│           │
│           ├── network
│           │   ├── RedisServer
│           │   ├── RespHandler
│           │   └── command
│           │       ├── Command
│           │       ├── CommandDispatcher
│           │       └── impl
│           │           ├── PingCommand
│           │           ├── SetCommand
│           │           ├── GetCommand
│           │           ├── DelCommand
│           │           ├── SetExCommand
│           │           ├── InfoCommand
│           │           └── ReplConfCommand
│           │
│           ├── persistence
│           │   └── AofManager
│           │
│           ├── server
│           │   └── ServerStats
│           │
│           └── storage
│               ├── KeyValueStore
│               ├── DataValue
│               └── scheduler
│                   └── ActiveExpirationScheduler
│
└── test
    └── ...
```

---

# 🚀 Implemented Features

## ✅ TCP Server

- TCP Socket 기반 Redis 서버
- Thread Pool을 이용한 다중 클라이언트 처리

---

## ✅ RESP Protocol

Redis Serialization Protocol(RESP)을 직접 파싱한다.

지원하는 응답 타입

- Simple String
- Bulk String
- Integer
- Null Bulk String

---

## ✅ Commands

현재 구현된 명령어

- PING
- SET
- GET
- DEL
- SETEX
- INFO
- REPLCONF

---

## ✅ Command Pattern

명령어마다 클래스를 분리하여 관리한다.

기존의 큰 switch 문 대신 Command Pattern을 적용해 새로운 명령을 쉽게 추가할 수 있도록 구성했다.

```
Client
      │
RedisServer
      │
RespHandler
      │
CommandDispatcher
      │
 Command
      │
 ├── PingCommand
 ├── SetCommand
 ├── GetCommand
 ├── DelCommand
 ├── SetExCommand
 ├── InfoCommand
 └── ReplConfCommand
```

---

## ✅ TTL

SETEX 명령으로 TTL을 설정할 수 있다.

```
SETEX name 10 gon
```

Key에 접근할 때 만료 여부를 확인하는 Lazy Expiration을 적용했다.

---

## ✅ Active Expiration

백그라운드 스레드가 주기적으로 만료된 Key를 제거한다.

- ScheduledExecutorService
- 주기적인 Key 검사
- 자동 삭제

---

## ✅ AOF Persistence

Append Only File(AOF)를 이용해 데이터를 저장한다.

지원 기능

- SET 기록
- DEL 기록
- 서버 시작 시 데이터 복구

---

## ✅ Replication (Basic)

기본적인 REPLCONF 명령을 지원한다.

현재는 기초적인 구조만 구현했으며 앞으로 PSYNC와 FULL RESYNC를 추가할 예정이다.

---

## ✅ INFO

서버 상태를 확인할 수 있는 INFO 명령을 구현했다.

현재 제공하는 정보

- MiniRedis Version
- Uptime
- Key Count

---

## ✅ MiniRedis Client

간단한 CLI를 만들어 redis-cli 없이도 서버를 테스트할 수 있다.

지원 기능

- 서버 연결
- 명령 입력
- RESP 응답 출력

예시

```text
miniRedis> SET name gon
OK

miniRedis> GET name
gon

miniRedis> INFO
# Server
miniRedis_version:0.1
```

---

# ✅ Unit Test

JUnit5와 AssertJ를 이용해 주요 기능을 테스트한다.

현재 테스트 대상

- Command Dispatcher
- Commands
- KeyValueStore
- RESP Handler
- TCP Server
- TTL
- Active Expiration

---

# 📚 Learning Goals

이 프로젝트를 만들면서 아래 내용을 직접 경험하는 것이 목표다.

- Redis 내부 구조
- TCP 네트워크 프로그래밍
- RESP 프로토콜
- Command Pattern
- 객체지향 설계
- 멀티스레드 서버
- 동시성(ConcurrentHashMap)
- TTL 및 만료 정책
- 파일 기반 영속성(AOF)
- 테스트 코드 작성

---

# 🗺 Roadmap

## Completed

- [x] TCP Server
- [x] RESP Parser
- [x] Command Pattern
- [x] SET / GET / DEL
- [x] SETEX
- [x] TTL
- [x] Active Expiration
- [x] AOF
- [x] Replication (Basic)
- [x] INFO Command
- [x] MiniRedis Client

## Next

- [ ] EXISTS
- [ ] KEYS
- [ ] INCR / DECR
- [ ] EXPIRE
- [ ] FLUSHDB
- [ ] AOF Rewrite
- [ ] Transactions (MULTI / EXEC)
- [ ] Pub/Sub
- [ ] RDB Snapshot
- [ ] PSYNC
- [ ] Full Replication
- [ ] Memory Eviction Policy
- [ ] Redis Streams

---

# 📖 Reference

- Redis Documentation
- Redis Source Code

