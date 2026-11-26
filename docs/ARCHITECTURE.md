# Kiến trúc Poker Network Game

## Luồng mạng

```text
Java Client
  -> Socket + DataOutputStream
  -> int32 length + UTF-8 JSON
  -> ServerSocket accept loop
  -> ClientHandler pool
  -> LobbyManager hoặc PokerTableRuntime command queue
  -> role-specific TableSnapshot
  -> Socket + DataInputStream
  -> Client receiver thread
  -> CLI hoặc Platform.runLater JavaFX
```

TCP chỉ cung cấp byte stream. `ProtocolCodec` tạo ranh giới message bằng length-prefix và dùng `readFully`, vì một lần `read` không tương ứng một message.

## OOP và concurrency

- `Card`, `PlayerHandState`, `BettingRound`, `PotManager`, `HandEvaluator` đóng gói game rules.
- `DeckProvider` cho phép production dùng deck ngẫu nhiên và test dùng fixed deck.
- `ClientEventListener` cho CLI và JavaFX dùng chung network client.
- Mỗi `PokerTableRuntime` có một thread mutation state. `ClientHandler` chỉ validate/route command.
- Timer callback không sửa state; nó enqueue một command có seat/version dự kiến.
- `ConcurrentHashMap` chỉ dùng cho connection, table và membership registries.

## Server authoritative và quyền xem bài

Client không gửi card, pot, winner hoặc stack mới. Server tính toàn bộ và tạo snapshot mới cho từng recipient:

- Player nhận public state và hai lá của chính mình.
- Observer nhận public state và hole cards của tất cả player.
- Observer không có seat; action bị trả `OBSERVER_CANNOT_ACT`.

## Persistence

Password được lưu bằng PBKDF2-HMAC-SHA256 với salt riêng. Session token ngẫu nhiên 256-bit, database chỉ lưu SHA-256 token.

Hand settlement chạy trong một transaction:

1. Lock user rows theo thứ tự user ID.
2. Ghi hand, player result và action history.
3. Cập nhật chip, games played và wins.
4. Ghi immutable wallet ledger.
5. Mark hand settled và commit.

Unique constraint trên ledger làm settlement idempotent. Nếu transaction lỗi, runtime phục hồi stack đầu hand và báo lỗi cho client.

