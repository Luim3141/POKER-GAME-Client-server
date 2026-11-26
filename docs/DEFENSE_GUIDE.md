# Hướng dẫn bảo vệ đồ án

## Java networking

- `ServerSocket` lắng nghe port; `accept()` tạo một `Socket` riêng cho connection đã hoàn tất TCP handshake.
- `Socket` đại diện kênh hai chiều. `InputStream` và `OutputStream` truyền byte, không truyền message.
- Length-prefix cần thiết để xử lý fragmentation, sticky packet và nhiều message liên tiếp.
- Client receiver thread chờ `readFully` ở background để JavaFX Application Thread không freeze.
- `ExecutorService` giới hạn số ClientHandler chạy đồng thời; không tạo thread vô hạn.

## Concurrency

- Nhiều ClientHandler có thể nhận action cùng lúc, nhưng mọi action của một bàn đi qua một queue.
- Chỉ table game thread mutate state nên không cần `synchronized` trên toàn bộ domain model.
- `stateVersion`, current seat và hand state loại action cũ hoặc sai lượt.
- Race condition xảy ra nếu hai thread cùng đọc và ghi state mà không có ordering/atomicity rõ ràng.

## Poker và authoritative server

- Client không được tính winner vì có thể gian lận và các client có thể cho kết quả khác nhau.
- Evaluator duyệt 21 tổ hợp năm lá từ bảy lá và so sánh category rồi kicker.
- Side pot được tách theo các contribution level. Folded player vẫn đóng tiền nhưng không có trong eligible set.
- Short all-in có thể tăng current bet nhưng không reopen raise cho người đã action; full raise thì reopen.

## JDBC và security

- `PreparedStatement` tách SQL khỏi data nên input không biến thành cú pháp SQL.
- Transaction giữ wallet, ledger và hand result nhất quán; rollback khi bất kỳ bước nào lỗi.
- Session token lưu dạng hash để lộ database không trực tiếp tạo được session hợp lệ.
- Bản demo dùng plain TCP, chỉ nên chạy localhost/mạng lab; Internet deployment cần TLS.

## Trình tự demo

1. Import schema và chạy server.
2. Chạy ba player clients và một observer.
3. Login, tạo/join bàn, ready và chơi một hand.
4. Chứng minh player không thấy bài đối thủ nhưng observer thấy toàn bộ.
5. Thử action sai lượt, all-in/side pot, disconnect/reconnect.
6. Mở phpMyAdmin kiểm tra hand, action history, wallet transaction và ranking.

