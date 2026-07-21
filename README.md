# MiniRedis

A Mini Redis implementation written in Java.

## Features & Learning Goals
- **In-Memory Engine**: ConcurrentHashMap 기반 Key-Value 저장소 및 Lazy Expiration(TTL) 구현
- **Persistence**: AOF(Append Only File) 기법을 통한 데이터 파일 저장 및 재시작 시 복구
- **Networking & RESP Protocol**: TCP Socket 기반 통신 및 Redis Standard Protocol(RESP) 파싱
- **Concurrency**: ThreadPool(ExecutorService)을 활용한 다중 클라이언트 동시 요청 처리
- **Distributed Replication**: Primary-Replica 구조의 실시간 비동기 명령어 전파 동기화

## Roadmap
- [x] v0.1 SET / GET / DEL (with TTL)
- [x] v0.2 Persistence (AOF)
- [x] v0.3 CLI (Interactive REPL)
- [x] v0.4 TCP Server & RESP Protocol
- [x] v0.5 Multi Thread (ThreadPool)
- [x] v0.6 Replication (Primary-Replica)