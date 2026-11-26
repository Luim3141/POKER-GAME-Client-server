# Domain glossary

- **AccountRole** — Quyền lâu dài của một tài khoản: Admin quản trị, Player chơi, Observer chỉ quan sát.
- **TableRole** — Góc nhìn tạm thời trong một bàn: Player có seat hoặc Observer không có seat. Không dùng để cấp quyền tài khoản.
- **Wallet** — Số coin bền vững của Player được lưu trong database.
- **TableStack** — Số coin đang tham gia bàn; trong kiến trúc hiện tại bằng toàn bộ Wallet khi Player ngồi xuống.
- **CoinRequest** — Yêu cầu một lần của Player xin Admin cộng một lượng coin xác định; chỉ được cộng sau khi Approved.

## Vinh quang

**WealthTier**: Bậc tài sản theo Wallet đã chốt, có thể tăng hoặc giảm; không phải số chip còn lại trong lượt cược.
_Avoid_: TableStack tier, best-ever wealth

**AchievementTier**: Bậc thành tích theo số ván và tỷ lệ ván nhận ít nhất một pot, có thể tăng hoặc giảm. Nhận pot gồm cả hòa và không nhất thiết có lãi ròng.
_Avoid_: Elo, tỷ lệ lãi ròng

**GlorySummary**: Thống kê công khai gồm Wallet đã chốt, số ván, số ván nhận pot và hai bậc tài sản/thành tích độc lập.
_Avoid_: Quyền tài khoản, phần thưởng coin
