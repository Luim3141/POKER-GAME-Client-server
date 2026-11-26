# Hiểu toàn bộ hệ thống Poker

Cẩm nang dành cho người mới đọc code và người xây ứng dụng với AI

Phiên bản source: 11/10/2026 • Java 21 • Windows • Game Poker Client–Server

Đây là bản đồ để hiểu dự án đang có, không phải báo cáo học phần và cũng không phải lời khẳng định hệ thống đã đạt chất lượng casino thương mại. Bạn có thể đọc từ đầu hoặc tìm một luồng cụ thể trong mục lục. Các mô tả bám source hiện tại; kết quả kiểm thử và những giới hạn chưa được kiểm chứng được tách riêng.

## 1. Bắt đầu từ bức tranh lớn

### 1.1. Game này thực chất là gì?

Bạn nhìn thấy bàn bài trên màn hình, nhưng ứng dụng không chỉ là giao diện đó. Hệ thống gồm chương trình client trên máy người chơi, chương trình server trên máy chủ và database trên máy chủ. Client nhận thao tác và vẽ bàn; server xác thực tài khoản, điều khiển ván và quyết định tiền thắng; database lưu tài khoản, phiên đăng nhập, lịch sử và sổ giao dịch chip.

Ví dụ: bạn bấm Raise lên 800. Client không tự trừ 800 rồi báo cho mọi người. Nó gửi yêu cầu đến server. Server kiểm tra lượt, số tiền và phiên bản bàn, áp dụng luật, rồi gửi trạng thái mới đến các client. Mọi người nhìn cùng một kết quả vì cùng nhận quyết định của server.

@figure architecture | Ba thành phần của hệ thống và ranh giới trách nhiệm

Server authoritative nghĩa là server có quyền quyết định cuối cùng. Đây là điểm cần nhớ xuyên suốt: UI đẹp, animation đúng hay nút đang sáng không đủ để một action hợp lệ. Kiểm tra ở server mới là hàng rào bảo vệ luật và số dư.

### 1.2. Đọc cẩm nang theo nhu cầu

| Bạn muốn hiểu | Đọc trước |
| --- | --- |
| Game gồm những gì và dùng công nghệ nào | Mục 1–3 |
| Vì sao bấm nút có thể làm máy khác thay đổi | Mục 4–6 |
| Luật cược, thắng tiền và tiếp tục ván | Mục 7–8 |
| Login, quyền Admin/Player/Observer, mất mạng | Mục 9–10 |
| Coin, database và bảo vệ dữ liệu | Mục 11–12 |
| Chạy trên máy chủ và gửi client cho bạn bè | Mục 13 |
| Tìm lỗi, đọc test và sửa code với AI | Mục 14–16 |

Không cần thuộc tất cả tên lớp trước khi đọc. Hãy theo một thao tác, chẳng hạn Login hoặc Call, qua từng thành phần. Khi hiểu hành trình đó, sơ đồ package sẽ dễ đọc hơn nhiều.

### 1.3. Những từ dễ bị nhầm

Một table là bàn tồn tại qua nhiều ván. Một hand là một ván từ chia bài đến trả thưởng. Một street là vòng Pre-flop, Flop, Turn hoặc River trong hand. Một turn là lượt quyết định của một người trong street. Một pot là quỹ chip; một hand có thể có main pot và nhiều side pot. Vì vậy pot không đồng nghĩa với một lượt chơi.

AccountRole là loại tài khoản: ADMIN, PLAYER hoặc OBSERVER. Role tại bàn là cách chiếu dữ liệu PLAYER hoặc OBSERVER. Hai khái niệm không thay thế nhau. Role dùng trong snapshot không tự cấp quyền tài khoản; bản hiện tại chỉ AccountRole.OBSERVER được gọi SPECTATE_TABLE.

Wallet là số dư bền vững trong database. Table stack là số chip phục vụ tính toán trong bàn. Lúc vào bàn, stack lấy từ wallet; giữa ván chúng không bắt buộc bằng nhau vì chip đang nằm trong pot. Sau settlement thành công, số dư được cập nhật theo kết quả cuối ván.

## 2. Từ source đến ứng dụng chạy được

### 2.1. Code, build, runtime khác nhau thế nào?

Source là các file .java, FXML, CSS và cấu hình mà ta chỉnh sửa. JDK chứa công cụ javac để biên dịch Java thành bytecode .class. Maven tổ chức build, tải thư viện và chạy test. JAR gom bytecode và tài nguyên thành một gói. Java runtime chạy bytecode; nó không tự cung cấp database hoặc server Poker.

Maven Wrapper là các script mvnw.cmd và thư mục .mvn giúp dùng phiên bản Maven đã quy định, kể cả khi máy chưa cài Maven trong PATH. Lần đầu cần mạng để tải Maven và thư viện. Một lần build thành công không tự chứng minh game đã chơi đúng; phần đó cần test và kiểm tra giao diện.

Gói gửi người chơi là app-image do jpackage tạo: thư mục có PokerClient.exe, các JAR và Java runtime đi kèm, sau đó nén ZIP. Người nhận phải giải nén toàn bộ thư mục, không chỉ lấy riêng EXE. Người nhận không cần JDK/Maven, nhưng vẫn cần kết nối đến server và tham gia mạng Radmin phù hợp. Cơ chế app-image được mô tả trong tài liệu JDK 21 [3].

### 2.2. Công nghệ được chọn và vai trò

| Thành phần | Đang dùng | Làm việc gì |
| --- | --- | --- |
| Ngôn ngữ/runtime | Java 21 | Server, client, concurrency, socket |
| Build | Maven Wrapper 3.9.9 | Build lặp lại, dependency và test |
| JSON | Jackson 2.18.2 | Đổi object/DTO sang JSON và ngược lại |
| Giao diện | JavaFX 21.0.6, FXML, CSS, Canvas | Trang, controls, bài và animation |
| Database | MariaDB của XAMPP | Dữ liệu bền vững và transaction |
| JDBC driver | MariaDB Connector/J 3.5.1 | Java server giao tiếp database |
| Test | JUnit 5.11.4, Surefire, Failsafe | Unit, DB/TCP integration, UI smoke |
| Mạng giữa máy | TCP qua Radmin VPN | LAN ảo phục vụ demo nhiều máy |

Không có Spring, ORM, Netty, connection-pool library hoặc frontend web/Three.js trong bản hiện tại. Một Maven module không có nghĩa client và server là một process: chúng vẫn là chương trình chạy độc lập, chỉ chung repository và một số model/protocol.

### 2.3. Client không tải bộ hình bài

Card gồm Rank và Suit, ví dụ ACE và SPADES. PlayingCardView/Canvas dựng mặt bài từ ký tự, màu, hình học và hiệu ứng. Server gửi giá trị lá bài trong JSON, không gửi file ảnh. Rồng, xu và hiệu ứng chia bài cũng do client vẽ. Điều này giảm tài nguyên phải phân phối và tránh nhầm phần minh họa với phần quyết định luật.

## 3. Bản đồ repository: đọc file nào trước?

### 3.1. Các thư mục quan trọng

| Đường dẫn từ gốc repository | Nội dung |
| --- | --- |
| pom.xml; .mvn; mvnw.cmd | Dependency, build, profile và Wrapper |
| src/main/java/com/poker/common | Cấu hình, mật khẩu, thời lượng chia bài |
| src/main/java/com/poker/model | Card, enum quyền/trạng thái, vinh quang |
| src/main/java/com/poker/protocol | Message, MessageType, ErrorCode, codec |
| src/main/java/com/poker/game | Deck, evaluator, betting, pots, phase |
| src/main/java/com/poker/server | Network, session, lobby, table runtime |
| src/main/java/com/poker/client | Socket client, listener, ClientState |
| src/main/java/com/poker/ui | Controller, cửa sổ, bài và animation |
| src/main/java/com/poker/persistence | JDBC repository, settlement, quản trị |
| src/main/resources/com/poker/ui | FXML, CSS và client.properties |
| src/test; database; docs | Test, schema/migration, hướng dẫn/chứng cứ |
| target | Kết quả build/test/release; không phải source |

### 3.2. Các điểm khởi chạy

PokerServerMain tạo cấu hình, kết nối thành phần server/persistence và mở listener. PokerJavaFxClientMain tải FXML, cấu hình Stage và hiện cửa sổ. PokerClientLauncher phục vụ launcher portable. PokerCliClientMain là client dòng lệnh dùng cùng tầng mạng. PokerAdminSetupMain là công cụ tạo Admin tại máy chủ, không phải nút cho người chơi tự nâng quyền.

Một đường đọc thuận tiện là PokerServerMain → PokerServer → ClientHandler → ServerController → LobbyManager → PokerTableRuntime. Phía client: PokerJavaFxClientMain → MainController → PokerClient → ServerConnection → ClientState → render. Bạn không cần đọc evaluator trước khi hiểu một frame TCP đi đâu.

### 3.3. Composition và encapsulation trong dự án

PokerTableRuntime phối hợp state bàn, betting, pots và evaluator. Các thành phần hợp thành một runtime thay vì tạo nhiều lớp kế thừa Player/Observer. DeckProvider là seam để thay bộ bài ngẫu nhiên bằng bộ bài cố định trong test; ClientEventListener giúp CLI và JavaFX dùng chung mạng. Encapsulation thể hiện ở việc chip/contribution được thay đổi qua nghiệp vụ, không từ một label trên UI.

@figure network_classes | Các lớp chính trong đường đi mạng; đây không phải sơ đồ mọi lớp của repository

## 4. Giao diện là một lớp hiển thị, không phải trọng tài

### 4.1. Trang và trạng thái

Sau login, MainController chọn trang theo quyền. Player có Home/Lobby, bàn chơi, History và Wallet/Coin Request. Observer có danh sách bàn và trang xem; không có nút cược hay quản lý wallet. Admin có dashboard tài khoản và yêu cầu coin, không tham gia gameplay.

Ở mỗi trang cần phân biệt loading, có dữ liệu, trống và lỗi. Nếu bảng chưa có bàn thì không có nghĩa socket hỏng. Nếu đăng nhập bị từ chối thì có thể kết nối vẫn hoạt động nhưng sai mật khẩu hoặc tài khoản bị khóa. Nếu đang chia bài thì nút cược ẩn/khóa vì chưa đến pha quyết định, không phải UI treo.

### 4.2. Thao tác đi qua những lớp nào?

MainController xử lý ý định người dùng; PokerClient tạo message và gửi qua ServerConnection. Receiver nhận message, cập nhật ClientState rồi phát event cho listener. JavaFX listener chuyển việc render về Platform.runLater. Canvas và các control đọc state đã được server gửi, không tự tính lại người thắng để cộng tiền.

JavaFX Application Thread là luồng riêng cho UI. Đọc socket bằng thao tác blocking ngay trên luồng này sẽ làm cửa sổ đứng hình. Vì vậy receiver chạy riêng, còn công việc gửi/kết nối có worker, và chỉ thay control trên luồng UI. Quy tắc luồng của JavaFX được nêu trong tài liệu Platform [5].

### 4.3. Thanh cược và thông tin quan sát

Kéo slider và nhập số là hai cách chọn cùng một amount; kéo không tự gửi action. Nhóm này rộng tối đa 220 logical px, nằm cạnh nhóm tiền và chỉ phục vụ lượt có quyền cược. WagerRange xử lý ánh xạ slider với giá trị long để tránh sai số ở số chip lớn. Server vẫn kiểm tra minimum raise và stack dù client đã giới hạn control.

Badge CHECK, CALL, RAISE, ALL-IN hoặc FOLD giúp nhìn hành động gần nhất tại ghế. Nhật ký hiện sáu action gần nhất; snapshot chứa tối đa 16 action để khôi phục. Pot/side pot hiện số tiền riêng. Thanh trạng thái dưới bàn đã thu gọn, không lấy mất phần lớn vùng chơi.

### 4.4. Hiệu ứng và cửa sổ

Full/Reduced/Off điều chỉnh chuyển động, không thay luật. Xu bay từ avatar vào pot chỉ minh họa paid > 0; kết quả thắng mới tạo luồng pot → người nhận và rồng ăn mừng. Người nhận pot có dấu hiệu thắng tĩnh; refund được tách với winnings. Resize, maximize/fullscreen và minimize không tạo thêm lượt cược. Khi minimize hoặc rời trang, animation được dọn; state server vẫn tiếp tục.

Giao diện bài tám ghế ở cửa sổ nhỏ và DPI cao đã có fixture kiểm thử. Các ảnh đó là chứng cứ layout, không phải chứng cứ một máy từ xa đã vào bàn.

## 5. TCP: tại sao hai máy hiểu được nhau?

### 5.1. Socket, địa chỉ và port

ServerSocket lắng nghe trên một địa chỉ/port. accept() nhận một kết nối và trả về Socket để trao đổi dữ liệu với đúng client đó. Một listener có thể phục vụ nhiều socket; mỗi người chơi không cần một port server riêng. Client dùng Socket kết nối tới địa chỉ máy chủ. Tài liệu Java ServerSocket mô tả ranh giới này [2].

TCP cho một luồng byte có thứ tự, không tự tạo ranh giới message ứng dụng. Một lần write không đảm bảo tương ứng một lần read. Hai message có thể được đọc gần nhau, hoặc một message phải đọc nhiều lần mới đủ. Đây là tính chất cần thiết kế framing, không phải lỗi mạng [1].

### 5.2. Frame của game

Mỗi frame gồm bốn byte length, tiếp theo là đúng length byte JSON UTF-8. DataOutputStream.writeInt dùng thứ tự byte lớn trước; receiver dùng readInt rồi readFully. Giới hạn payload là 1 byte đến 1 MiB. Không dùng Java Object Serialization.

```text
[int32 payloadLength][payloadLength byte JSON UTF-8]
Ví dụ JSON có 240 byte → header mô tả 240, rồi mới tới JSON.
Length tính bằng byte UTF-8, không phải số ký tự tiếng Việt.
```

ProtocolCodec kiểm tra length trước khi cấp buffer, đọc đủ payload rồi mới parse JSON. EOF nghĩa peer đóng hoặc stream đã kết thúc; frame lỗi không được đem vào game. Test codec cố tình chia payload thành các lần đọc nhỏ, nối nhiều frame và gửi length/JSON không hợp lệ.

### 5.3. Envelope: phong bì bên ngoài payload

| Trường | Ý nghĩa trong hệ thống |
| --- | --- |
| version | Phiên bản protocol; hiện bằng 1 |
| type | Loại request/event, chẳng hạn PLAYER_ACTION |
| requestId | UUID để nối response với request |
| sessionId | Token phiên; ví dụ tài liệu phải che đi |
| tableId; handId | Chỉ đúng bàn/ván cần xử lý |
| seq | Phiên bản state; không phải sequence TCP |
| timestamp | Thời điểm tạo message, milliseconds |
| payload | Dữ liệu riêng theo type |

Ví dụ minh họa cấu trúc, không phải token hoặc hand thật:

```json
{"version":1,"type":"PLAYER_ACTION",
 "requestId":"00000000-0000-4000-8000-000000000001",
 "sessionId":"<redacted>","tableId":"<table-id>",
 "handId":"<hand-id>","seq":42,"timestamp":0,
 "payload":{"action":"CALL","amount":0}}
```

seq giúp server nhận ra thao tác dựa trên snapshot đã cũ. Ví dụ bạn thấy cần Call 200, nhưng trước khi bấm một action khác đã thay đổi bàn; server có thể trả STALE_STATE thay vì áp dụng một quyết định không còn đúng ngữ cảnh. requestId giúp client biết phản hồi thuộc lần bấm nào; nó không tự làm mọi request trở thành idempotent.

### 5.4. Request, response và event không giống nhau

Request thể hiện ý định: JOIN_TABLE hoặc PLAYER_ACTION. Response trả lời yêu cầu: ACTION_ACCEPTED/ACTION_REJECTED hoặc ERROR. Event thông báo thay đổi chủ động, như TABLE_ACTION, TABLE_STATE hay HAND_RESULT. Một action có thể làm nhiều event tới mọi người và một response tới người gửi. ACTION_ACCEPTED không có nghĩa chỉ có một frame về UI.

Client không tự retry cược khi mất mạng vì gửi lại có thể làm phát sinh hành động thứ hai. Nó resume và lấy snapshot trước khi cho người dùng quyết định tiếp. Token/session không được dùng làm thông tin log hoặc ảnh minh họa.

### 5.5. Những nhóm message thực sự được dùng

| Nhóm | Request/event tiêu biểu trong source |
| --- | --- |
| Tài khoản/phiên | REGISTER, LOGIN, RESUME_SESSION, LOGOUT; LOGIN_SUCCESS/LOGIN_FAILED |
| Sức khỏe connection | PING/PONG |
| Lobby và lịch sử | GET_LOBBY/LOBBY_SNAPSHOT; GET_HISTORY/HISTORY_SNAPSHOT |
| Ghế/bàn | CREATE_TABLE, JOIN_TABLE, LEAVE_TABLE/LEAVE_SEAT, READY/UNREADY |
| Mời | INVITE_PLAYER, INVITE_RECEIVED, INVITE_RESPONSE |
| Quan sát | SPECTATE_TABLE, STOP_SPECTATE; snapshot theo quyền |
| Vòng chơi | HAND_STARTED, TABLE_STATE, COMMUNITY_UPDATE, SHOWDOWN, HAND_RESULT |
| Cược | ACTION_REQUIRED, PLAYER_ACTION, ACTION_ACCEPTED/REJECTED, TABLE_ACTION |
| Khôi phục | STATE_SNAPSHOT |
| Coin/Admin | SUBMIT_COIN_REQUEST, GET_COIN_REQUESTS, REVIEW_COIN_REQUEST, ADMIN_ACCOUNT_ACTION, ADMIN_DASHBOARD, WALLET_UPDATED |

MessageType còn một số tên như TAKE_SEAT, PRIVATE_HOLE_CARDS, OBSERVER_HOLE_CARDS hoặc POT_UPDATE; không vì enum có tên mà khẳng định có API riêng đang dùng. Bài và pot hiện chủ yếu đi trong snapshot. Ranking nằm trong dữ liệu lobby; không có một command GET_RANKING riêng. History lấy dữ liệu hand đã lưu, không phải replay live hand từng frame.

## 6. Thread, queue và chống xung đột

### 6.1. Tại sao một luồng không đủ?

accept() phải chờ kết nối, read() phải chờ dữ liệu, UI phải phản hồi thao tác, còn timer cần chạy dù một người đang im lặng. Nếu tất cả làm trên một luồng, một socket chậm có thể chặn toàn game. Nhưng nếu nhiều luồng cùng sửa stack thì có thể cùng chấp nhận hai cược dựa trên một số dư.

Giải pháp là tách công việc chờ I/O khỏi chủ sở hữu state. ClientHandler đọc/validate/route, không trực tiếp sửa bàn. Mỗi PokerTableRuntime có queue và một game thread. Hai request cùng đến được enqueue và xử lý lần lượt. Scheduler chỉ đẩy timeout hoặc kết thúc chia bài vào queue; nó không tự thay turn.

@figure threads | Các luồng và queue trong đường đi command/event

### 6.2. Những hàng rào cụ thể

Reader pool server giới hạn 64 handler; SynchronousQueue không tích một hàng dài socket chưa có reader. Mỗi connection có outbound FIFO 128 frame và một virtual writer. Game thread enqueue message, không phải tự chờ socket chậm ghi hết byte. Khi outbound không còn chỗ, connection chậm bị đóng thay vì làm cả bàn chờ vô hạn.

ConcurrentHashMap phù hợp tra cứu connection/session đồng thời, nhưng không thay thế luật nhất quán của hand. Per-user operation lock bảo vệ các thao tác có thể giao nhau giữa admission vào bàn và quản trị wallet. Thứ tự khóa, connection identity và state version đều cần đúng; chỉ thêm synchronized khắp nơi không giải quyết được ranh giới nghiệp vụ.

DB settlement vẫn là công việc đồng bộ tại kết thúc hand trên runtime; writer riêng không làm DB trở thành bất đồng bộ. Nếu DB chậm, bàn đó có thể chờ settlement. Chưa có nghiệm thu fault-injection DB treo dài hạn, nên không diễn giải kiến trúc này thành khả năng chịu mọi outage.

### 6.3. Một tình huống race cụ thể

Admin đang approve coin cho A trong lúc A bấm Join. Nếu chỉ kiểm tra “không seated” rồi thả khóa trước khi cộng tiền, A có thể vào bàn với stack cũ. Hệ thống dùng cùng per-user lock cho Join/Create và wallet/admin mutation để kiểm tra và thao tác theo một vùng nhất quán. Transaction DB bảo vệ dữ liệu bền vững, còn lock trong server bảo vệ điều kiện seat trong memory; cần cả hai.

Một race khác là socket cũ gửi Ready sau khi token đã resume trên socket mới. Registry xác định connection hiện hành; socket bị thay không được tiếp tục thay bàn. Receiver client cũng giữ tham chiếu connection riêng để luồng cũ không đóng nhầm socket mới.

## 7. Một ván Poker từ Ready đến ván tiếp theo

### 7.1. Vào bàn và bắt đầu

Bàn có 2–8 ghế. Player chỉ ngồi một bàn tại một thời điểm. Người tạo chọn small blind; big blind bằng hai lần small blind. Không có cơ chế mua buy-in riêng: stack khi vào bàn phản ánh wallet hiện tại. Khi đủ điều kiện và người chơi Ready, server tạo hand, gán vị trí và chia bài.

Tài khoản mới có 10.000 chip. Bộ bài chuẩn gồm 52 lá, 13 rank và bốn suit, không Joker. Mỗi người có hai lá riêng; board lần lượt thêm ba lá ở Flop, một ở Turn, một ở River. Lời mời chỉ hỗ trợ đưa người chơi tới bàn; chấp nhận lời mời vẫn phải qua điều kiện quyền, chỗ trống và membership, không bỏ qua luật Join.

TableStatus mô tả WAITING, IN_HAND hoặc BETWEEN_HANDS. HandPhase DEALING/BETTING là pha bên trong IN_HAND, không phải một bàn khác. Sau hand, Ready được reset; người chơi phải Ready cho hand tiếp theo. Không tự bắt đầu ván mới chỉ vì animation đã kết thúc.

@figure hand | Vòng đời hand; các quyết định thực hiện tại server

### 7.2. Thứ tự cược: giữ vị trí, không giữ một actor cho mọi vòng

Dealer (D), small blind (SB) và big blind (BB) giữ vị trí trong một hand rồi dịch sang hand sau. Ở bàn từ ba người: SB sau D, BB sau SB; Pre-flop bắt đầu sau BB. Flop/Turn/River bắt đầu từ người còn có thể hành động sau D. Fold/all-in được bỏ qua khi tìm actor.

Ví dụ ba ghế theo chiều kim đồng hồ A=D, B=SB, C=BB: Pre-flop đi A → B → C; các street sau đi B → C → A nếu mọi người còn hành động. Việc người cuối Pre-flop trở thành người đầu vòng sau có thể đúng khi cấu hình còn lại thay đổi, không phải cứ gặp là lỗi. Cần kiểm tra D/SB/BB và người đã fold/all-in.

Heads-up là ngoại lệ quan trọng: Dealer đồng thời là SB, hành động trước Pre-flop; BB hành động trước ở các vòng sau. Luật được kiểm tra bằng test riêng thay vì cố ép một thứ tự giống nhau cho tất cả street.

### 7.3. Chia bài chậm nhưng quyền cược không phụ thuộc animation

Pre-flop chia hai lượt theo thứ tự ghế sau Dealer. Flop phát ba lá riêng; Turn/River mỗi vòng một lá. Một lá bay 350 ms, các lần bắt đầu cách 450 ms; server thêm 200 ms dự phòng cuối chuỗi. Trong DEALING không có actor, prompt hoặc turn timeout. Khi pha xong, BETTING bắt đầu và actor có đủ 20 giây.

@figure dealing | Timer cược chỉ bắt đầu sau pha chia bài của server

Snapshot chứa phase, duration, remaining và số lá để client đến muộn có thể bắt kịp. Client dùng thời gian đơn điệu sau khi nhận snapshot cho tiến độ minh họa. Nếu prompt đến, quyền cược theo state server, không phải chờ animation trên một máy yếu. All-in runout đi từng street, không bật ngay năm lá cùng lúc.

### 7.4. Cược hợp lệ và timeout

CHECK chỉ được khi không cần trả thêm. CALL trả phần còn thiếu, có thể hết stack. BET đặt cược đầu vòng; RAISE nâng tổng mức cược trong street; FOLD từ bỏ quyền nhận pot. ALL_IN cam kết stack còn lại, nhưng không có nghĩa luôn là một full raise.

RAISE TO 800 là tổng contribution trong street, không phải luôn trừ thêm 800. Nếu bạn đã trả 300 thì phần mới trả là 500. BettingRound theo dõi currentBet, lastFullRaiseSize, contribution và quyền raise. Full raise có thể mở lại quyền; short all-in không mặc nhiên mở lại cho người đã action.

Timeout tự CHECK nếu toCall=0, ngược lại FOLD. TABLE_ACTION đánh dấu automatic và cho mọi người biết kết quả. Timer không gia hạn vì người dùng minimize, lag hoặc reconnect. Một lượt được xử lý trước thì callback timeout cũ phải bị guard vô hiệu, không được áp dụng hai lần.

### 7.5. Một lần bấm Call đi hết hệ thống

@figure action | Request action, validation, sự kiện toàn bàn và response riêng

Thứ tự kiểm tra gồm authenticated connection, quyền tài khoản, bàn/ván, pha, stateVersion, current player và amount. Action bị từ chối không sinh coin animation. Action hợp lệ phát TABLE_ACTION trước khi chuyển street hoặc trả thưởng, nên action cuối vòng vẫn có bằng chứng trong log.

## 8. Bài mạnh, pot, trả thưởng và vinh quang

### 8.1. Tìm năm lá tốt nhất

Mỗi người có hai lá riêng và dùng chung năm lá board. HandEvaluator duyệt 21 tổ hợp năm lá từ bảy lá, so category rồi tie-break ranks. Wheel A-2-3-4-5 dùng Ace thấp. Kicker quyết định khi cùng category; nếu board là bộ tốt nhất thì nhiều người có thể hòa chính xác. Server làm việc này; hình lá bài trên client chỉ là trình bày.

Từ thấp lên cao: High Card, One Pair, Two Pair, Three of a Kind, Straight, Flush, Full House, Four of a Kind, Straight Flush. Royal Flush là Straight Flush cao nhất, không cần một category riêng. Đôi là hai lá cùng rank; sảnh là năm rank liên tiếp; thùng là năm lá cùng suit. Khi cùng category, các rank cần so sánh được lưu trong HandValue thay vì chỉ dùng tên bộ bài.

### 8.2. Ví dụ side pot có thể tính bằng tay

A đóng tổng 100, B đóng 300, C đóng 300. Main pot là 100×3=300, A/B/C đủ điều kiện. Side pot là 200×2=400, chỉ B/C đủ điều kiện. Nếu A mạnh nhất và B thắng C, A nhận 300 còn B nhận 400. Không được cho A lấy cả 700 vì A không góp vào tầng thứ hai.

Người Fold vẫn đóng tiền đã cược nhưng không eligible. Tiền cược không được đối thủ theo đủ có thể hoàn riêng dưới dạng uncalled refund. Khi hòa, chia đều; odd chip được trao theo thứ tự ghế clockwise sau Dealer. Những trường hợp hết người đủ điều kiện trong một tầng pot khi nhiều người rời bàn có chính sách của dự án và test riêng, không nên coi mọi hành vi demo là luật casino tổng quát.

### 8.3. Tiền thắng khác thay đổi ròng của wallet

Giả sử đầu hand bạn có 10.000, đóng 300 rồi nhận pot 700. Final stack là 10.400; net settlement là +400, không phải +700. Người thua đóng 300 nhận 0 thì net=-300. Nếu hoàn lại 100 cược không được call, 100 đó không phải tiền thắng.

Server tính payout và transaction cập nhật wallet trước khi thông báo kết quả thành công. Client hiển thị tiền nhận pot, tiền hoàn và số dư theo dữ liệu trả về. Hiệu ứng chúc mừng không cộng tiền. Nếu transaction thất bại, không được bật thắng rồi giữ wallet cũ như thể đã thành công.

### 8.4. Tiếp tục và vinh quang

HAND_RESULT kết thúc hand; BETWEEN_HANDS đưa bàn về chờ Ready mới. Người offline được dọn ghế sau settlement. Người thắng có khung sáng và nhãn đến hand kế tiếp, nhưng Ready không chờ rồng/xu bay xong.

GlorySummary tính các dấu hiệu vinh quang từ chip và lịch sử thắng/thua. Chúng là thông tin trang trí/thống kê, không tăng quyền Admin, không quyết định bài mạnh và không tạo tiền. Đừng nhầm thứ hạng hiển thị với authorization.

## 9. Tài khoản, phiên đăng nhập và quyền

### 9.1. Tự kết nối không đồng nghĩa tự đăng nhập

Mở client sẽ thử kết nối endpoint mặc định. Người dùng vẫn phải nhập tài khoản để login/register. Nếu server chưa sẵn sàng, login có thể thử nối lại; không cần một nút Connect riêng trước đó. Sai endpoint, VPN offline và sai password là ba loại lỗi khác nhau.

@figure login | Luồng login từ client tới session và trang theo quyền

PasswordHasher dùng PBKDF2-HMAC-SHA256, salt ngẫu nhiên và 210.000 iterations, không lưu password gốc. Session token ngẫu nhiên 256-bit, mặc định 24 giờ; database chỉ lưu SHA-256 của token. Hash password có salt và nhiều vòng làm chậm dò password; hash token dùng để tránh lưu bí mật phiên trực tiếp. Hai kiểu hash giải quyết hai mục đích khác nhau.

Token là bí mật gần giống chìa khóa tạm thời. Có token hợp lệ thì server vẫn kiểm tra expiry, revoke và account status. UI giữ token phục vụ resume trong phiên ứng dụng; không có cam kết “Remember me” an toàn trên disk. Protocol chưa có TLS riêng, nên chỉ triển khai trong mạng VPN tin cậy, không mở Internet công khai.

### 9.2. Ma trận quyền tối giản

| Tính năng | ADMIN | PLAYER | OBSERVER |
| --- | --- | --- | --- |
| Dashboard/quản lý tài khoản | Có | Không | Không |
| Tạo bàn, join seat, Ready, cược | Không | Có | Không |
| Lobby | Không | Có | Có |
| Spectate | Không | Không | Có |
| History và coin request cá nhân | Không | Có | Không |
| Xem bài khi ngồi chơi | Không | Chỉ bài mình | Không ngồi |
| Xem bài trong observer projection | Không | Không | Khi spectate |

Public registration luôn tạo Player. Admin được tạo bằng công cụ host; Player ↔ Observer do Admin đổi khi thỏa điều kiện. Các hàng trên bám authorize() hiện tại: Player không được SPECTATE_TABLE/STOP_SPECTATE. Không suy quyền từ tên Role hoặc từ một control có thể nhìn thấy trên giao diện; server mới quyết định khả năng thực tế.

### 9.3. Projection: đừng gửi bí mật rồi mới ẩn trên UI

Server tạo snapshot riêng cho từng recipient. Player nhận board và bài của mình; observer nhận bài mọi người theo yêu cầu đồ án. UI Player không chỉ dùng CSS để giấu bài người khác sau khi đã nhận cả bộ. Nếu JSON đã chứa bí mật, người dùng sửa client vẫn đọc được.

Observer thấy mọi hole card là tính năng đặc thù, không phải mô hình công bằng của poker tiền thật. Không dùng tài khoản observer để cộng tác với player trong một buổi chơi muốn giữ tính cạnh tranh.

## 10. Mất mạng, rời bàn và server crash

### 10.1. Khi nào server biết một người đã mất kết nối?

Đóng socket chủ động thường được nhận qua EOF/lỗi I/O. Với mất mạng không báo ngay, heartbeat gửi PING mỗi 10 giây và theo dõi PONG; quá khoảng 30 giây không phản hồi thì đóng connection. Vì vậy trạng thái online không đổi tức thì ở mọi kiểu sự cố. Heartbeat cũng dọn runtime trống và đối soát disconnect callback bị bỏ lỡ.

### 10.2. Chính sách seat hiện tại

@figure disconnect | Disconnect không giữ ghế vô hạn; xử lý khác nhau trong và ngoài hand

Ngoài hand, seat được giải phóng ngay khi disconnect được xử lý. Trong hand, server giữ bài/contribution để resume trước khi ván kết thúc; đánh dấu DISCONNECTED và giữ deadline. Offline all-in vẫn eligible và có thể thắng. Kết quả được settlement trước, sau đó seat còn offline mới bị loại.

Nếu resume kịp, connection mới thay connection cũ và snapshot đúng role khôi phục bàn. Nếu hand đã xong và seat đã dọn, người dùng về lobby với wallet persisted. Reconnect không tạo player mới trong cùng hand và không phát lại coin animation từ action history.

LEAVE_TABLE khác mất mạng: đây là ý định rời; giữa hand có thể Fold, chip đã đóng giữ trong pot và cleanup theo vòng đời hand. Logout revoke phiên thay vì chỉ giấu giao diện. Admin Lock vừa revoke session vừa ngắt connection, đồng thời xử lý rời bàn.

### 10.3. Crash không có nghĩa khôi phục được hand

Hand đang chạy chủ yếu ở memory; wallet được thay bền vững khi settlement. Nếu server crash trước commit, không có payout mới trong database và không thể tự hồi lại đúng hand đang chia/cược. Hand record hiện được ghi trong quy trình settlement, không nên mô tả database như đã lưu toàn bộ live hand từng giây hoặc luôn có một record để đánh dấu aborted.

Transaction đã commit thì wallet vẫn còn dù client chưa nhận kết quả; login/resume sau đó phải đọc lại persisted balance. Nếu DB lỗi lúc settlement, transaction rollback và runtime đi theo nhánh báo lỗi/khôi phục stack, không giả thành công. Chưa nghiệm thu mất điện đúng mọi thời điểm và DB treo, nên vẫn cần backup và vận hành có kiểm soát.

## 11. Coin request và quản trị

### 11.1. Một yêu cầu đi từ Player tới Admin

Player gửi amount từ 1 đến 100.000. Mỗi Player chỉ có một PENDING request. Có thể gửi khi đang seated nhưng Admin chỉ approve lúc target không còn seat; nếu còn bàn, server trả PLAYER_AT_TABLE và giữ PENDING. Player rời bàn rồi Admin duyệt lại, không phải tạo request mới.

@figure coin_sequence | Approval là một transaction; dashboard được push sau thay đổi

Approval dùng amount đã lưu trong request, không tin số tiền Admin/client tự đính kèm. Server lock request và user, kiểm tra PENDING, cập nhật balance, ledger, trạng thái request và audit rồi commit. Hai lần approve không được cộng hai lần; database có unique constraint liên quan request để bảo vệ ledger.

Sau thay đổi, Admin nhận dashboard mới, Player nhận COIN_REQUEST_SNAPSHOT và WALLET_UPDATED. Đây là server push, không phải bắt Admin bấm refresh liên tục. Khi reconnect, trạng thái lấy từ database, không từ một danh sách tạm trong UI.

### 11.2. Các thao tác tài khoản

| Thao tác | Điều kiện/hiệu quả quan trọng |
| --- | --- |
| Lock | Khóa đăng nhập, revoke các session, ngắt connection và xử lý rời bàn |
| Unlock | Mở trạng thái account; không tự đăng nhập hộ |
| Đổi Player/Observer | Offline, không seated/spectating, không pending request |
| Reset password | Offline/không seated; password tạm dùng như password bình thường, revoke session cũ |
| Adjust coin | Offline/không seated, lý do bắt buộc, không âm balance hoặc overflow |

UI và server không cho sửa/xóa tài khoản ADMIN bằng các action này. Audit không chứa password hoặc token. Yêu cầu thêm coin là chip demo, không phải thanh toán tiền thật, không có mua coin, ngân hàng hay nhiều currency.

## 12. Database, JDBC và tính toàn vẹn

### 12.1. Chín bảng lưu những gì?

| Bảng | Vai trò |
| --- | --- |
| users | Username, password hash/salt, role/status, chips và counters |
| sessions | Hash token, user, expiry và revoke |
| poker_tables | Owner, blinds, số ghế, trạng thái bàn |
| hands | Bàn, dealer, board, trạng thái/marker settlement |
| hand_players | Ghế, stack đầu/cuối, contribution, kết quả |
| hand_actions | Action theo sequence trong hand |
| coin_requests | Amount, PENDING/APPROVED/REJECTED, reviewer, note |
| wallet_transactions | Ledger net change, balance_after, hand/request/admin |
| admin_audit_log | Ai quản trị ai, loại action và chi tiết an toàn |

@figure erd_game | View ERD gameplay: khóa và quan hệ vật lý trong schema

@figure erd_accounts | View ERD tài khoản/coin; các FK phụ được ghi chú để hình không chồng khối

PK định danh một dòng; FK nối đến dòng tồn tại trong bảng khác; UNIQUE ngăn một loại trùng lặp. Một user có nhiều session hoặc nhiều hand_players; một hand có nhiều action. Nullable hand_id trong ledger cho phép giao dịch coin không liên quan hand. ERD được tách hai view để đọc được; không có nghĩa có hai database.

### 12.2. Transaction là gì, tại sao phải có?

Một settlement gồm nhiều update. Nếu cộng tiền cho người thắng rồi chương trình lỗi trước khi trừ người thua, tổng tiền bị lệch. Transaction gom các bước: hoặc commit toàn bộ, hoặc rollback toàn bộ. SELECT ... FOR UPDATE khóa các dòng cần sửa trong transaction, không phải khóa mọi user mãi mãi. JDBC quản lý connection/statement, tham số và commit/rollback [6].

Settlement kiểm tra marker hand, lock user rows theo thứ tự, ghi hand_players/actions, ledger ròng, update chips/statistics và mark settled. Unique key (hand_id,user_id,transaction_type) giúp ngăn ledger settlement trùng. Idempotency ở đây nghĩa xử lý lại cùng settlement không làm payout thêm lần nữa, không phải mọi command đều được phép retry tùy ý.

PreparedStatement tách SQL với dữ liệu tham số. Username hoặc note không được nối trực tiếp vào chuỗi SQL. Việc này giảm nguy cơ SQL injection, nhưng không thay thế kiểm tra quyền và validation nghiệp vụ. Schema/index/transaction của MariaDB là phần server, không được gói vào client [7].

### 12.3. Schema và migration không phải cùng một thao tác

database/schema.sql phục vụ database mới. V2__account_roles_and_coin_requests.sql nâng database cũ, có thêm role/status và bảng quản trị coin. Không import schema mới tùy tiện lên dữ liệu đang dùng. Trước migration phải backup; server kiểm tra cấu trúc cần thiết, không âm thầm tự nâng schema mỗi lần mở.

Kiểm tra “một pending request” dùng transaction/lock trên user và kiểm tra trạng thái, không phải một partial unique index trong schema. Không nhìn một bảng rồi suy ra mọi luật đã được DB tự enforce; luật một user một seat thuộc state server.

## 13. Chạy hệ thống trên máy chủ và máy khách

### 13.1. Ba địa chỉ thường gặp

| Giá trị | Nghĩa đúng |
| --- | --- |
| 127.0.0.1 | Chính máy đang chạy; client máy khác không dùng nó để gọi server của bạn |
| 0.0.0.0 | Server bind mọi IPv4 interface; không phải địa chỉ người chơi nhập |
| 192.0.2.10 | IP minh họa cho server Radmin. Thay bằng địa chỉ VPN của máy chủ khi kết nối. |
| Port 9090 | Poker TCP giữa client và server |
| Port 3306 | MariaDB local trên host; không mở cho client |

@figure deployment | Triển khai dự kiến trên nhiều máy qua LAN ảo Radmin, không phải chứng cứ nghiệm thu từ xa

Radmin tạo LAN ảo giữa các máy cùng network. Chủ host tạo mạng và cung cấp tên/mật khẩu ngoài tài liệu này; client join network đó. Radmin công bố cơ chế mã hóa của VPN [4], nhưng ứng dụng Poker chưa có TLS riêng. Không port-forward 9090 ra Internet rồi coi mật khẩu login đủ bảo vệ đường truyền.

### 13.2. Mỗi lần máy chủ chạy

1. Bật Radmin, kiểm tra adapter/IP của host; các máy phải cùng network.
2. Start MySQL trong XAMPP; xác nhận database/config đang dùng đúng môi trường.
3. Nếu vừa đổi source, chạy mvnw.cmd package trước. Không chỉ chạy lại EXE client rồi để server source cũ.
4. Chạy run-server.cmd trong repository; giữ process server hoạt động, xem log lỗi startup.
5. Kiểm tra TCP 9090 và firewall; không tạo lại firewall rule tùy tiện mỗi lần.
6. Mở client trên host hoặc máy khác, login, vào bàn và Ready.

```powershell
# Tại D:\AI project\LTM
.\mvnw.cmd package
.\run-server.cmd
# Cửa sổ PowerShell khác: kiểm tra listener
Get-NetTCPConnection -State Listen -LocalPort 9090
```

run-server.cmd dùng exec:java để chạy class, không phải lệnh tự compile mọi source mới. Nên build trước khi triển khai. Không restart server lúc còn hand cần hoàn tất; client mới nhất cần đi cùng server source tương ứng. Bản ZIP chỉ là client, không cài MariaDB/server hộ máy chủ.

### 13.3. Máy người chơi cần làm gì?

1. Cài/bật Radmin và join network do host cung cấp.
2. Giải nén ZIP toàn bộ; vào thư mục PokerClient và mở PokerClient.exe.
3. Client thử nối tự động. Nếu host đổi IP/port, sửa ở trang Login.
4. Đăng ký Player hoặc login tài khoản có sẵn; Admin tạo/đổi role ở phía host.
5. Chọn bàn, Join và Ready; hoặc Xem nếu role/context cho phép.

```powershell
# Chạy tại máy client; không phải thao tác sửa firewall
Test-NetConnection 192.0.2.10 -Port 9090
```

Không cài XAMPP, không import SQL và không nhập DB password trên máy người chơi. client.properties là tài nguyên mặc định trong client; UI Login là chỗ chỉnh endpoint khi cần. Bản portable kèm hướng dẫn máy khách. Không chạy EXE ngay bên trong cửa sổ xem ZIP.

### 13.4. Firewall và lỗi kết nối

setup-radmin-firewall.ps1 mở inbound TCP 9090 trên interface Radmin VPN, remote range 26.0.0.0/8, cần PowerShell Administrator [8]. Tên adapter/phạm vi phải đúng thực tế. Không tắt toàn bộ Windows Firewall; không mở 3306 cho máy bạn bè. Máy client thông thường cần outbound đến 9090, không cần rule inbound server giống host.

Nếu ping được nhưng Test-NetConnection thất bại, kiểm tra listener/bind/firewall. Ping thất bại không luôn chứng minh TCP hỏng vì ICMP có thể bị chặn. Nếu TCP được nhưng login lỗi, chuyển sang kiểm tra account/password/schema/server log thay vì tiếp tục sửa VPN.

### 13.5. Cấu hình nằm ở đâu?

AppConfig đọc POKER_HOST, POKER_PORT, POKER_DB_URL, POKER_DB_USER, POKER_DB_PASSWORD và POKER_TURN_SECONDS từ environment của server. Mặc định host=0.0.0.0, port=9090, turn=20; DB URL trỏ MariaDB trên 127.0.0.1:3306/poker_game. Chỉ đặt credential tại host, không gửi file chứa nó trong ZIP. Một process chỉ nhìn environment lúc khởi chạy; sửa biến ở cửa sổ khác không tự cấu hình lại server đang chạy.

build-portable-client.ps1 đóng client bằng JDK/jpackage và copy runtime. Đợt này chạy bằng PowerShell 7 thành công; gọi script dưới Windows PowerShell 5 có thể coi output stderr của Java là lỗi với ErrorActionPreference=Stop. Người nhận client không cần chạy script build. Muốn thay source phải build/test rồi tạo release mới, không chỉnh trực tiếp class trong app-image.

## 14. Test: biết chắc điều gì, chưa biết điều gì?

### 14.1. Các lớp kiểm thử

Unit test chạy những logic nhỏ, như evaluator, betting, pot, codec và WagerRange. DB/TCP integration mở nhiều socket và database test để kiểm tra hợp tác giữa thành phần. UI smoke tải FXML thật với fixture, kiểm tra layout và thao tác. Một fixture dựng bàn tám người không đồng nghĩa có tám người thật ở tám máy từ xa.

```powershell
.\mvnw.cmd clean test
.\mvnw.cmd package
# Chỉ khi MariaDB test đã chuẩn bị, dùng poker_test
.\mvnw.cmd -Pdb-it verify
```

Chạy integration trên database test, không đổi URL sang poker_game để thử cho tiện. Surefire/Failsafe XML có tests/failures/errors/skipped; không cộng file XML cũ còn sót vào một lần chạy mới. target có thể bị clean xóa, nên chứng cứ quan trọng được lưu riêng.

### 14.2. Chứng cứ source 11/10/2026

Lần review trước trong ngày: 74 unit và 12 DB/TCP integration test pass, gồm 10 ServerEndToEndIT và 2 JdbcPersistenceIT. Một test có seed 20261011 chạy 60 hand, bàn 2–8 người, kiểm tra stale rejection, balance không âm, chip conservation và settlement một lần. Đây là 60 kịch bản hand trong test, không phải 60 test method riêng.

UiSmokeIT đã pass ở DPI 100/125/150/175/200%, mỗi mức một scenario có nhiều assertion. Chứng cứ và giới hạn nằm trong docs/AUDIT_RESULTS_20261011.md, XML lưu ở target/review-qa-20261011. Lần build portable này chạy lại 74 unit tests, 0 failure/error; kiểm tra ZIP khớp target/classes và runtime. Không gọi DB/UI chạy trước là vừa chạy lại khi đóng ZIP.

Launcher EXE mới mở và phản hồi bình thường trong smoke launch; hiện không có IP Radmin mặc định được gán trên máy nên chưa xác nhận auto-connect qua VPN của release này. Các lần demo nhiều client trên cùng host ở phiên trước không thay thế nghiệm thu máy thứ hai. Chưa thử Windows sạch không Java, load/soak nhiều bàn, DB treo hoặc mất điện ở mọi điểm transaction.

### 14.3. Đọc kết quả thay vì chỉ đọc dòng BUILD SUCCESS

Một build pass cho biết các bước đã được cấu hình và thực hiện không lỗi; nó không bao phủ mọi kịch bản. Test evaluator pass không chứng minh slider hiển thị đúng ở DPI 200%. Test UI pass không chứng minh ledger transaction rollback. Đọc tên test, input và assertion để biết điều gì thật sự được bảo vệ.

Một cách đọc hữu ích là tìm test cạnh class cần sửa, xác định invariant, rồi chạy targeted test trước full suite. Invariant là điều phải luôn đúng: chip không tự sinh trong gameplay, không nhận pot hai lần, observer không action, callback cũ không chuyển pha nữa.

## 15. Tìm lỗi theo luồng, không đoán mò

### 15.1. Checklist theo triệu chứng

| Triệu chứng | Kiểm tra theo thứ tự |
| --- | --- |
| Mở client nhưng không kết nối | Radmin/IP → TCP 9090 → process server → host/port Login |
| TCP được, login không được | Account status → credential → DB/schema → server error |
| Ready mà chưa chia | Số người/stack/Ready → snapshot → table runtime |
| Không cược được | DEALING? đúng lượt? seq/hand còn mới? amount/raise hợp lệ? |
| Thấy thắng nhưng balance không đúng kỳ vọng | Payout vs net/refund → HAND_RESULT → wallet ledger/commit |
| Người offline còn ghế | Trong hand còn quyền resume? heartbeat đã hết? settlement xong chưa? |
| UI đứng hoặc lỗi thread | Có I/O trên FX thread? callback đã Platform.runLater? |
| Raise sai số | amount là tổng hay tăng thêm? slider/input? server validation? |

### 15.2. Thu thập bằng chứng an toàn

Ghi thời điểm, user ID không bí mật, table/hand ID, street, phase, seq, action và expected/actual. Chụp UI nếu cần nhưng không để password/token/log DB credential lọt vào ảnh. Đừng chỉ gửi “game lỗi”; gửi “A ở TURN, toCall=200, bấm CALL tại seq=42, server trả STALE_STATE sau khi B action” thì mới lần được đường đi.

Kiểm tra từ ngoài vào trong: network rồi auth rồi state rồi luật rồi DB. Không sửa firewall vì một ERROR nghiệp vụ; không cộng tiền thủ công trong client để che một settlement lỗi. Khi tái hiện trên fixture/test database, giữ seed/bộ bài và lệnh test để người sau chạy lại được.

### 15.3. Những file cần xem cho từng vấn đề

Framing/EOF: ProtocolCodec và ClientHandler/ServerConnection. Socket cũ/reconnect: PokerClient, ConnectionRegistry, ServerController. Sai lượt/chia bài: PokerTableRuntime, BettingRound, DealTiming. Pot: PotManager và HandEvaluator. Wallet/coin: JdbcHandRepository và JdbcAccountAdministration. Layout/animation: MainController, FXML/CSS, TableEffects, PlayingCardView và ClientWindow.

## 16. Sửa hệ thống bằng AI mà không phá phần đang chạy

### 16.1. Đặt yêu cầu theo hành vi và ranh giới

Một prompt tốt không chỉ nói “fix Poker”. Hãy nêu trạng thái ban đầu, thao tác, kết quả mong muốn, nơi không được thay đổi và cách test. Ví dụ: “Giảm chiều cao tray khi IN_HAND, giữ pot/log/Ready, không đổi game rules; kiểm tra cửa sổ 960×640 và DPI 200%; không đóng ZIP trước khi tôi yêu cầu.”

Với lỗi tiền: yêu cầu tái hiện và test transaction/payout trước, không để AI vá label bằng số giả. Với lỗi reconnect: thử cả ngoài hand, giữa hand, all-in, socket cũ và resume sau settlement. Những nhánh này có hợp đồng khác nhau.

### 16.2. Quy trình nhỏ nhưng đủ

1. Đọc source và test hiện tại, kiểm tra file chưa commit/đang sửa.
2. Xác định một nguyên nhân và cách tái hiện; không refactor toàn repository cùng lúc.
3. Thêm assertion cho hành vi lỗi, sửa đúng lớp sở hữu nghiệp vụ.
4. Chạy targeted test, full unit, integration/UI phù hợp rủi ro.
5. Review callers, permissions, connection identity, timer và transaction.
6. Cập nhật docs/PROGRESS.md; ghi rõ test đã chạy và phần chưa thử.
7. Khi release, build mới, kiểm tra gói, giữ bản cũ để rollback ở ranh giới hand an toàn.

### 16.3. Những thay đổi thường cần chạm nhiều nơi

Thêm field snapshot cần cập nhật DTO, server projection, client state/render và test. Thêm command cần MessageType, route, authorization, payload validation, client API, UI và contract test. Đổi luật cược phải sửa ở game/runtime trước; UI chỉ cập nhật controls theo prompt. Đổi schema cần migration/backup và repository/test, không chỉ sửa một file SQL.

Nếu không hiểu một patch, hỏi “caller nào phụ thuộc, dữ liệu nào có thể mất, invariant nào được test?” trước khi chạy. AI tạo code nhanh nhưng không phải bằng chứng code đúng. Đừng đưa password/token vào prompt để nhờ debug; cung cấp bản đã redacted.

## 17. Từ điển tra nhanh

| Thuật ngữ | Cách hiểu trong game này |
| --- | --- |
| DTO / snapshot | Dữ liệu truyền hoặc ảnh chụp state, không phải object sống của server |
| Codec / framing | Đóng/mở message thành byte và xác định ranh giới frame |
| Envelope / payload | Metadata chung và dữ liệu riêng của một type |
| Listener / callback | Hàm được gọi khi có message/sự kiện, không nhất thiết luồng UI |
| Queue / worker | Hàng đợi và luồng lấy việc ra xử lý |
| Authoritative | Server quyết định trạng thái chính thức |
| Projection | Tạo dữ liệu được phép thấy theo recipient/role |
| Correlation | Ghép response với requestId gốc |
| Stale state | Yêu cầu dựa trên phiên bản bàn đã cũ |
| Heartbeat | PING/PONG để phát hiện connection mất đáp ứng |
| Resume | Xác thực session rồi gắn connection mới với context còn hợp lệ |
| Transaction | Nhóm thao tác DB commit tất cả hoặc rollback |
| Ledger | Lịch sử biến động chip; không phải tổng pot trên bàn |
| Idempotency | Lặp một nghiệp vụ được bảo vệ không làm phát sinh kết quả lần hai |
| Race condition | Kết quả sai do thao tác đồng thời xen kẽ không được bảo vệ |
| Deadlock | Các luồng chờ khóa của nhau không thể tiến tiếp |
| Fixture / seed | Dữ liệu dựng/test hoặc điểm khởi đầu ngẫu nhiên để tái hiện |
| DPI / logical px | Tỉ lệ hiển thị và đơn vị layout, khác pixel vật lý |
| App-image | Thư mục ứng dụng và runtime; cần giữ đủ nội dung khi giải nén |

## 18. Nguồn, độ tin cậy và đường đọc tiếp

### 18.1. Nguồn nội bộ của cẩm nang

Ưu tiên source trong src/main, schema/migration trong database và test thật trong src/test. Nhật ký docs/PROGRESS.md, docs/AUDIT_RESULTS_20261011.md và các XML review-qa-20261011 cung cấp chứng cứ chạy. README và GAMEPLAY_UX.md giúp đọc theo chức năng. Các sơ đồ tại docs/handbook/diagrams giữ source PlantUML và render cục bộ; deployment là cấu hình dự kiến, không phải ảnh chụp nghiệm thu máy thứ hai.

Gói release ngày 11/10 có runtime Java 21.0.10, mặc định 192.0.2.10:9090 và client bytecode hiện tại; không mang persistence/server launcher/JDBC driver. Server source trên host vẫn phải triển khai tương ứng. Cẩm nang mới không ghi đè báo cáo học phần, mẫu trường hoặc các release cũ.

### 18.2. Tài liệu chính thức để học thêm

[1] IETF, RFC 9293 — Transmission Control Protocol (TCP). https://www.rfc-editor.org/rfc/rfc9293.html

[2] Oracle, Java 21 — ServerSocket API. https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/net/ServerSocket.html

[3] Oracle, JDK 21 jpackage — Packaging Overview. https://docs.oracle.com/en/java/javase/21/jpackage/packaging-overview.html

[4] Radmin VPN — Security. https://www.radmin-vpn.com/security/

[5] OpenJFX 21 — Platform API. https://openjfx.io/javadoc/21/javafx.graphics/javafx/application/Platform.html

[6] Oracle Java Tutorials — JDBC Transactions. https://docs.oracle.com/javase/tutorial/jdbc/basics/transactions.html

[7] MariaDB — START TRANSACTION. https://mariadb.com/docs/server/reference/sql-statements/transactions/start-transaction

[8] Microsoft — New-NetFirewallRule. https://learn.microsoft.com/en-us/powershell/module/netsecurity/new-netfirewallrule

Các nguồn này giải thích nền tảng/API, không chứng minh project đã hiện thực mọi tính năng tài liệu của hãng nêu. Cấu hình thật, luồng được xử lý và kết quả test phải đối chiếu repository. Nếu source đổi sau ngày cẩm nang, cập nhật phần liên quan trước khi dùng làm hướng dẫn release mới.

### 18.3. Khi đã đọc xong

Hãy tự lần theo ba việc: một lần Login, một lần Call và một lần approve coin. Với mỗi việc, xác định ai tạo request, ai có quyền kiểm tra, state sống ở đâu, DB được sửa khi nào và UI nhận gì. Nếu trả lời được năm câu này, bạn đã có nền để đọc phần lớn hệ thống mà không cần đoán theo tên nút.
