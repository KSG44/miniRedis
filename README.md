# MiniRedis

> Java로 Redis를 직접 구현하며 Redis의 내부 동작을 학습하는 프로젝트

## 📌 Project Overview

MiniRedis는 Redis의 핵심 기능을 Java로 직접 구현하는 프로젝트입니다.

단순히 Redis 명령어를 따라 만드는 것이 아니라, 실제 Redis의 내부 구조와 설계 방식을 이해하는 것을 목표로 합니다.

개발 과정에서는 기능을 하나씩 추가하며 리팩토리 패턴, 멀티스레드, 네트워크 프로그래밍, 파일 입출력(AOF), 테스트 코드 등을 함께 학습합니다.

---

## 🛠 Tech Stack

- Java 21
- TCP Socket
- JUnit5
- AssertJ
- Gradle

---

## 📂 Project Structure

```
src
├── network
│   ├── RedisServer
│   ├── RespHandler
│   └── command
│       ├── Command
│       ├── CommandDispatcher
│       └── impl
├── storage
│   ├── KeyValueStore
│   ├── DataValue
│   └── scheduler
├── persistence
│   └── AofManager
└── test
```

---

# Implemented Features

## ✅ TCP Server

- TCP Socket 기반 Redis 서버 구현
- 다중 클라이언트 연결 지원

---

## ✅ RESP Protocol

Redis Serialization Protocol(RESP) 파싱

지원 응답

- Simple String
- Bulk String
- Integer
- Null Bulk String

---

## ✅ Basic Commands

현재 구현된 명령어

- PING
- SET
- GET
- DEL
- SETEX
- REPLCONF

---

## ✅ Command Pattern

기존의 거대한 switch문을 제거하고 Command Pattern으로 리팩토링했습니다.

각 명령어를 독립적인 클래스로 분리하여 유지보수성과 확장성을 높였습니다.

```
Client
      │
RedisServer
      │
RespHandler
      │
CommandDispatcher
      │
  Command Interface
      │
 ├── SetCommand
 ├── GetCommand
 ├── DelCommand
 ├── PingCommand
 ├── SetExCommand
 └── ReplConfCommand
```

---

## ✅ TTL (Lazy Expiration)

SETEX 명령을 통해 TTL을 설정할 수 있습니다.

```
SETEX name gon 10
```

키에 접근(GET)할 때 만료 여부를 확인하여 자동 삭제합니다.

---

## ✅ Active Expiration

실제 Redis처럼 백그라운드 스레드가 주기적으로 만료된 Key를 삭제합니다.

- ScheduledExecutorService 사용
- 주기적인 Key 검사
- 만료된 데이터 자동 제거

---

## ✅ AOF Persistence

Append Only File(AOF)를 구현했습니다.

지원 기능

- SET 기록
- DEL 기록
- 서버 시작 시 AOF 복구

---

## ✅ Replication (Basic)

기본적인 REPLCONF 명령을 지원합니다.

향후 PSYNC 및 FULL RESYNC를 구현할 예정입니다.

---

## ✅ Unit Test

JUnit5와 AssertJ를 이용하여 주요 기능을 테스트합니다.

현재 테스트 대상

- Command Dispatcher
- Commands
- TTL
- Active Expiration
- KeyValueStore

---

# Learning Goals

이 프로젝트를 통해 다음 내용을 학습하는 것을 목표로 합니다.

- Redis 내부 구조
- TCP 네트워크 프로그래밍
- Command Pattern
- 멀티스레드
- 동시성(ConcurrentHashMap)
- 파일 시스템(AOF)
- 테스트 코드 작성
- 객체지향 설계

---

# Roadmap

## Completed

- [x] TCP Server
- [x] RESP Parser
- [x] SET / GET / DEL
- [x] TTL
- [x] Active Expiration
- [x] AOF
- [x] Replication (Basic)
- [x] Command Pattern

## In Progress

- [ ] INFO Command
- [ ] AOF Rewrite
- [ ] Transactions
- [ ] Pub/Sub
- [ ] RDB Snapshot
- [ ] PSYNC
- [ ] Full Replication
- [ ] Memory Eviction Policy
- [ ] Redis Streams

---

# Reference

- Redis Documentation
- Redis Source Code
- 