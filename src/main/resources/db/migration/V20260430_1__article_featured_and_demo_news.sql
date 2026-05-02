IF COL_LENGTH('dbo.Articles', 'is_featured') IS NULL
BEGIN
    ALTER TABLE dbo.Articles
        ADD is_featured BIT NOT NULL
            CONSTRAINT DF_Articles_is_featured DEFAULT 0;
END;

IF NOT EXISTS (
    SELECT 1
    FROM sys.indexes
    WHERE name = 'IX_Articles_featured_published_at'
      AND object_id = OBJECT_ID('dbo.Articles')
)
BEGIN
    CREATE INDEX IX_Articles_featured_published_at
        ON dbo.Articles (is_featured DESC, published_at DESC);
END;

IF NOT EXISTS (SELECT 1 FROM dbo.ArticleCategories WHERE slug = 'xe-cu')
BEGIN
    INSERT INTO dbo.ArticleCategories (name, slug, description, sort_order, active)
    VALUES (N'Xe cũ', 'xe-cu', N'Tin tức, mẹo chọn và đánh giá xe đã qua sử dụng.', 1, 1);
END;

IF NOT EXISTS (SELECT 1 FROM dbo.ArticleCategories WHERE slug = 'hang-xe')
BEGIN
    INSERT INTO dbo.ArticleCategories (name, slug, description, sort_order, active)
    VALUES (N'Hãng xe', 'hang-xe', N'Bài viết phân tích các thương hiệu và dòng xe nổi bật.', 2, 1);
END;

IF NOT EXISTS (SELECT 1 FROM dbo.ArticleCategories WHERE slug = 'dong-co')
BEGIN
    INSERT INTO dbo.ArticleCategories (name, slug, description, sort_order, active)
    VALUES (N'Động cơ', 'dong-co', N'Kiến thức nền tảng về động cơ, hộp số và vận hành.', 3, 1);
END;

IF NOT EXISTS (SELECT 1 FROM dbo.ArticleCategories WHERE slug = 'bao-duong')
BEGIN
    INSERT INTO dbo.ArticleCategories (name, slug, description, sort_order, active)
    VALUES (N'Bảo dưỡng', 'bao-duong', N'Lịch bảo dưỡng và kinh nghiệm giữ xe bền bỉ.', 4, 1);
END;

IF NOT EXISTS (SELECT 1 FROM dbo.ArticleCategories WHERE slug = 'kinh-nghiem-lai-thu')
BEGIN
    INSERT INTO dbo.ArticleCategories (name, slug, description, sort_order, active)
    VALUES (N'Kinh nghiệm lái thử', 'kinh-nghiem-lai-thu', N'Checklist và kỹ năng đánh giá xe khi lái thử.', 5, 1);
END;

IF NOT EXISTS (SELECT 1 FROM dbo.ArticleCategories WHERE slug = 'thi-truong')
BEGIN
    INSERT INTO dbo.ArticleCategories (name, slug, description, sort_order, active)
    VALUES (N'Thị trường', 'thi-truong', N'Góc nhìn giá xe, xu hướng mua bán và cập nhật thị trường.', 6, 1);
END;

DECLARE @ArticleSeedSql NVARCHAR(MAX) = N'
IF NOT EXISTS (SELECT 1 FROM dbo.Articles WHERE slug = ''huong-dan-kiem-tra-xe-cu-5-phut'')
BEGIN
    INSERT INTO dbo.Articles (
        title, slug, summary, content, thumbnail_url, author_id, category_id, status, is_featured, published_at, view_count, is_deleted
    )
    SELECT
        N''Hướng dẫn kiểm tra xe cũ trong 5 phút trước khi đặt lịch lái thử'',
        ''huong-dan-kiem-tra-xe-cu-5-phut'',
        N''Checklist nhanh giúp người mua xác định ngoại thất, khoang máy và lịch sử sử dụng trước khi xuống tiền.'',
        N''<p>Một buổi xem xe hiệu quả không cần quá dài, nhưng cần đúng thứ tự. Hãy bắt đầu từ ngoại thất: quan sát độ đồng đều của màu sơn, khe hở giữa các cánh cửa và dấu hiệu va chạm ở cản trước, cản sau.</p><p>Tiếp theo là khoang máy. Một khoang máy sạch không đồng nghĩa với xe tốt, nhưng dầu loang, dây điện độ chế và mùi khét là tín hiệu cần dừng lại để hỏi kỹ người bán.</p><p>Khi vào cabin, hãy kiểm tra độ mòn vô-lăng, cần số, bàn đạp và ghế lái. Nếu đồng hồ hiển thị quãng đường thấp nhưng nội thất mòn nhiều, xe có thể đã bị tua công-tơ-mét.</p><p>Cuối cùng, đừng bỏ qua lịch sử bảo dưỡng và giấy tờ gốc. Một chiếc xe cũ minh bạch luôn dễ định giá và ít phát sinh rủi ro hơn về sau.</p>'',
        ''https://placehold.co/1200x800/0f172a/f8fafc?text=Kiem+tra+xe+cu'',
        NULL,
        c.id,
        ''published'',
        1,
        ''2026-04-25T02:00:00'',
        186,
        0
    FROM dbo.ArticleCategories c
    WHERE c.slug = ''xe-cu'';
END;

IF NOT EXISTS (SELECT 1 FROM dbo.Articles WHERE slug = ''toyota-vios-cu-giu-gia-nhu-the-nao-2026'')
BEGIN
    INSERT INTO dbo.Articles (
        title, slug, summary, content, thumbnail_url, author_id, category_id, status, is_featured, published_at, view_count, is_deleted
    )
    SELECT
        N''Toyota Vios cũ giữ giá như thế nào trong năm 2026?'',
        ''toyota-vios-cu-giu-gia-nhu-the-nao-2026'',
        N''Phân tích vì sao Vios vẫn là mẫu sedan được săn tìm trên thị trường xe đã qua sử dụng.'',
        N''<p>Toyota Vios vẫn nằm trong nhóm xe cũ dễ mua, dễ bán vì chi phí sử dụng thấp, phụ tùng phổ biến và mức tiêu hao nhiên liệu ổn định.</p><p>Điểm mạnh lớn nhất của Vios là độ thanh khoản. Người mua lần đầu thường ưu tiên một mẫu xe ít rủi ro, máy bền, phụ tùng sẵn và dễ tìm xưởng sửa chữa. Vios đáp ứng rất tốt ba tiêu chí đó.</p><p>Tuy nhiên, mức giữ giá cao cũng kéo theo việc nhiều xe đẹp bị đẩy giá. Khi so sánh các xe cùng đời, hãy nhìn thêm lịch sử bảo dưỡng, số chủ cũ và tình trạng gầm thay vì chỉ nhìn odo.</p><p>Nếu ưu tiên sự an tâm và bán lại nhanh sau 2 đến 3 năm, Vios vẫn là lựa chọn khó bỏ qua trong nhóm sedan phổ thông.</p>'',
        ''https://placehold.co/1200x800/1d4ed8/f8fafc?text=Toyota+Vios+Cu'',
        NULL,
        c.id,
        ''published'',
        1,
        ''2026-04-24T03:00:00'',
        244,
        0
    FROM dbo.ArticleCategories c
    WHERE c.slug = ''hang-xe'';
END;

IF NOT EXISTS (SELECT 1 FROM dbo.Articles WHERE slug = ''dong-co-tang-ap-co-nen-mua-xe-cu'')
BEGIN
    INSERT INTO dbo.Articles (
        title, slug, summary, content, thumbnail_url, author_id, category_id, status, is_featured, published_at, view_count, is_deleted
    )
    SELECT
        N''Động cơ tăng áp: Có nên mua xe cũ dùng turbo hay không?'',
        ''dong-co-tang-ap-co-nen-mua-xe-cu'',
        N''Ưu điểm của turbo là mạnh và tiết kiệm nhiên liệu, nhưng người mua xe cũ cần hiểu rõ lịch thay dầu và tình trạng làm mát.'',
        N''<p>Động cơ tăng áp đem lại cảm giác lái linh hoạt hơn khi tăng tốc, nhất là ở dải tua thấp. Với người đi phố nhiều, đây là lợi thế dễ cảm nhận.</p><p>Nhưng turbo chỉ bền khi xe được thay dầu đúng chuẩn, đúng thời điểm và không bị tắt máy đột ngột sau khi vận hành nặng. Xe cũ thiếu lịch sử bảo dưỡng rõ ràng sẽ tiềm ẩn nhiều rủi ro hơn xe hút khí tự nhiên.</p><p>Khi xem xe, hãy chú ý tiếng hú lạ ở tua cao, khói xả bất thường và mức tiêu hao dầu máy. Những dấu hiệu này có thể liên quan trực tiếp đến cụm turbo hoặc đường dầu.</p><p>Nếu người bán cung cấp đầy đủ lịch sử bảo dưỡng và xe vận hành ổn định, xe dùng turbo vẫn là lựa chọn rất đáng cân nhắc.</p>'',
        ''https://placehold.co/1200x800/14532d/f8fafc?text=Dong+co+Turbo'',
        NULL,
        c.id,
        ''published'',
        1,
        ''2026-04-23T02:30:00'',
        132,
        0
    FROM dbo.ArticleCategories c
    WHERE c.slug = ''dong-co'';
END;

IF NOT EXISTS (SELECT 1 FROM dbo.Articles WHERE slug = ''6-moc-bao-duong-sau-khi-mua-xe-cu'')
BEGIN
    INSERT INTO dbo.Articles (
        title, slug, summary, content, thumbnail_url, author_id, category_id, status, is_featured, published_at, view_count, is_deleted
    )
    SELECT
        N''6 mốc bảo dưỡng nên làm ngay sau khi mua xe cũ'',
        ''6-moc-bao-duong-sau-khi-mua-xe-cu'',
        N''Bộ lọc, dầu máy, dầu hộp số và hệ thống phanh là bốn nhóm việc nên xử lý sớm để yên tâm sử dụng.'',
        N''<p>Dù người bán nói xe vừa bảo dưỡng, chủ mới vẫn nên thiết lập lại mốc chăm xe của riêng mình. Việc thay dầu máy, lọc gió, lọc điều hòa và kiểm tra nước làm mát giúp bạn chủ động hơn trong 6 tháng đầu sử dụng.</p><p>Hai hạng mục thường bị bỏ sót là dầu hộp số và dầu phanh. Đây là những chất lỏng ảnh hưởng trực tiếp tới độ êm và an toàn khi vận hành.</p><p>Ngoài ra, hãy cân chỉnh thước lái, kiểm tra lốp và ắc-quy. Những chi phí này không quá lớn nhưng tạo khác biệt rõ rệt khi đi đường dài.</p><p>Một kế hoạch bảo dưỡng sớm sẽ giúp xe cũ vận hành ổn định và giữ giá tốt hơn khi bán lại.</p>'',
        ''https://placehold.co/1200x800/7c2d12/f8fafc?text=Bao+duong+xe'',
        NULL,
        c.id,
        ''published'',
        0,
        ''2026-04-22T04:00:00'',
        117,
        0
    FROM dbo.ArticleCategories c
    WHERE c.slug = ''bao-duong'';
END;

IF NOT EXISTS (SELECT 1 FROM dbo.Articles WHERE slug = ''7-cau-hoi-nen-hoi-khi-lai-thu-xe-cu'')
BEGIN
    INSERT INTO dbo.Articles (
        title, slug, summary, content, thumbnail_url, author_id, category_id, status, is_featured, published_at, view_count, is_deleted
    )
    SELECT
        N''7 câu hỏi nên hỏi nhân viên khi lái thử xe cũ'',
        ''7-cau-hoi-nen-hoi-khi-lai-thu-xe-cu'',
        N''Danh sách câu hỏi ngắn gọn giúp bạn khai thác đúng thông tin về lịch sử xe, bảo dưỡng và giấy tờ.'',
        N''<p>Một buổi lái thử hiệu quả không chỉ nằm ở cảm giác sau vô-lăng mà còn ở chất lượng câu hỏi bạn đặt ra. Hãy bắt đầu từ lịch sử sử dụng: xe đã qua bao nhiêu đời chủ, chạy chủ yếu trong phố hay đường trường.</p><p>Sau đó hỏi về lịch sử bảo dưỡng lớn, thay lốp, thay ắc-quy, sửa hộp số hoặc đại tu máy. Đây là những hạng mục ảnh hưởng trực tiếp đến chi phí vận hành trong tương lai.</p><p>Cuối cùng, đừng quên xác nhận giấy tờ pháp lý và quy trình sang tên. Một chiếc xe minh bạch luôn đáng giá hơn một chiếc xe giá rẻ nhưng thông tin mập mờ.</p>'',
        ''https://placehold.co/1200x800/5b21b6/f8fafc?text=Lai+thu+xe'',
        NULL,
        c.id,
        ''published'',
        0,
        ''2026-04-21T08:00:00'',
        98,
        0
    FROM dbo.ArticleCategories c
    WHERE c.slug = ''kinh-nghiem-lai-thu'';
END;

IF NOT EXISTS (SELECT 1 FROM dbo.Articles WHERE slug = ''thi-truong-suv-7-cho-da-qua-su-dung-2026'')
BEGIN
    INSERT INTO dbo.Articles (
        title, slug, summary, content, thumbnail_url, author_id, category_id, status, is_featured, published_at, view_count, is_deleted
    )
    SELECT
        N''Thị trường SUV 7 chỗ đã qua sử dụng đang dịch chuyển ra sao?'',
        ''thi-truong-suv-7-cho-da-qua-su-dung-2026'',
        N''Nhu cầu xe gia đình tăng mạnh khiến nhiều mẫu SUV 7 chỗ đời 2019-2022 giữ giá cao hơn kỳ vọng.'',
        N''<p>Phân khúc SUV 7 chỗ cũ vẫn giữ nhiệt nhờ nhu cầu gia đình đi tỉnh, du lịch và chở đủ người. Những mẫu xe có lịch sử bảo dưỡng đại lý và nội thất còn mới thường bán rất nhanh.</p><p>Ngược lại, các xe chạy dịch vụ hoặc từng va chạm nặng bắt đầu bị người mua sàng lọc kỹ hơn. Khoảng chênh giá giữa xe đẹp và xe trung bình ngày càng lớn.</p><p>Xu hướng này cho thấy người mua xe cũ hiện quan tâm nhiều hơn đến chất lượng thực tế thay vì chỉ nhìn đời xe.</p>'',
        ''https://placehold.co/1200x800/0f766e/f8fafc?text=SUV+7+cho'',
        NULL,
        c.id,
        ''published'',
        0,
        ''2026-04-20T02:00:00'',
        163,
        0
    FROM dbo.ArticleCategories c
    WHERE c.slug = ''thi-truong'';
END;

IF NOT EXISTS (SELECT 1 FROM dbo.Articles WHERE slug = ''so-sanh-honda-city-va-toyota-vios-cu'')
BEGIN
    INSERT INTO dbo.Articles (
        title, slug, summary, content, thumbnail_url, author_id, category_id, status, is_featured, published_at, view_count, is_deleted
    )
    SELECT
        N''So sánh Honda City và Toyota Vios cũ: Chọn xe nào hợp nhu cầu gia đình?'',
        ''so-sanh-honda-city-va-toyota-vios-cu'',
        N''Hai mẫu sedan hạng B đều bền bỉ, nhưng khác nhau khá rõ về cảm giác lái, chi phí sử dụng và độ giữ giá.'',
        N''<p>Honda City nổi bật với không gian ghế sau thoáng và cảm giác lái chắc tay hơn. Toyota Vios lại có lợi thế về phụ tùng rẻ, dễ bán lại và mức độ phổ biến rất cao.</p><p>Nếu ưu tiên sự lành tính trong sử dụng hằng ngày, Vios là lựa chọn an toàn. Nếu muốn khoang hành khách rộng và cảm giác lái cân bằng hơn, City có nhiều điểm đáng chú ý.</p><p>Ở thị trường xe cũ, quyết định nên dựa vào tình trạng xe cụ thể nhiều hơn tên mẫu xe. Một chiếc City bảo dưỡng chuẩn có thể đáng mua hơn một chiếc Vios thiếu lịch sử rõ ràng, và ngược lại.</p>'',
        ''https://placehold.co/1200x800/9a3412/f8fafc?text=City+vs+Vios'',
        NULL,
        c.id,
        ''published'',
        0,
        ''2026-04-19T03:30:00'',
        141,
        0
    FROM dbo.ArticleCategories c
    WHERE c.slug = ''hang-xe'';
END;

IF NOT EXISTS (SELECT 1 FROM dbo.Articles WHERE slug = ''dau-hieu-he-thong-phanh-can-bao-duong-gap'')
BEGIN
    INSERT INTO dbo.Articles (
        title, slug, summary, content, thumbnail_url, author_id, category_id, status, is_featured, published_at, view_count, is_deleted
    )
    SELECT
        N''Dấu hiệu hệ thống phanh cần được bảo dưỡng gấp trên xe cũ'',
        ''dau-hieu-he-thong-phanh-can-bao-duong-gap'',
        N''Bàn đạp mềm, xe lệch lái khi phanh và tiếng rít lớn là ba dấu hiệu không nên bỏ qua.'',
        N''<p>Hệ thống phanh xuống cấp thường biểu hiện sớm qua cảm giác bàn đạp và quãng đường dừng xe. Nếu phải đạp sâu hơn bình thường hoặc xe bị lệch khi phanh, bạn nên kiểm tra ngay.</p><p>Má phanh mòn, đĩa phanh xước hoặc dầu phanh cũ đều có thể khiến hiệu quả phanh suy giảm. Trên xe cũ mới mua, đây là hạng mục nên ưu tiên hàng đầu.</p><p>Kiểm tra và thay thế đúng lúc không chỉ giúp an toàn hơn mà còn tránh phát sinh chi phí lớn ở heo dầu và đĩa phanh.</p>'',
        ''https://placehold.co/1200x800/991b1b/f8fafc?text=He+thong+phanh'',
        NULL,
        c.id,
        ''published'',
        0,
        ''2026-04-18T06:00:00'',
        75,
        0
    FROM dbo.ArticleCategories c
    WHERE c.slug = ''bao-duong'';
END;

IF NOT EXISTS (SELECT 1 FROM dbo.Articles WHERE slug = ''co-nen-mua-xe-da-chay-dich-vu'')
BEGIN
    INSERT INTO dbo.Articles (
        title, slug, summary, content, thumbnail_url, author_id, category_id, status, is_featured, published_at, view_count, is_deleted
    )
    SELECT
        N''Có nên mua xe đã từng chạy dịch vụ hay không?'',
        ''co-nen-mua-xe-da-chay-dich-vu'',
        N''Xe từng chạy dịch vụ không phải lúc nào cũng xấu, nhưng người mua cần soi kỹ khung gầm, nội thất và lịch bảo dưỡng.'',
        N''<p>Điểm đáng ngại nhất của xe dịch vụ là cường độ sử dụng cao. Tuy nhiên, nếu chủ xe bảo dưỡng đều và thay thế hao mòn đúng hạn, chất lượng thực tế vẫn có thể chấp nhận được.</p><p>Khi xem xe, hãy chú ý độ mòn ghế, bệ bước, vô-lăng và tay nắm cửa. Những chi tiết này thường phản ánh đúng hơn đồng hồ công-tơ-mét.</p><p>Xe dịch vụ chỉ phù hợp khi giá bán đủ hấp dẫn để bù cho rủi ro còn lại. Nếu mức chênh không đáng kể, xe gia đình minh bạch vẫn là lựa chọn tốt hơn.</p>'',
        ''https://placehold.co/1200x800/334155/f8fafc?text=Xe+chay+dich+vu'',
        NULL,
        c.id,
        ''published'',
        0,
        ''2026-04-17T02:00:00'',
        109,
        0
    FROM dbo.ArticleCategories c
    WHERE c.slug = ''xe-cu'';
END;

IF NOT EXISTS (SELECT 1 FROM dbo.Articles WHERE slug = ''cach-doc-thong-so-momen-xoan-va-cong-suat'')
BEGIN
    INSERT INTO dbo.Articles (
        title, slug, summary, content, thumbnail_url, author_id, category_id, status, is_featured, published_at, view_count, is_deleted
    )
    SELECT
        N''Cách đọc thông số mô-men xoắn và công suất khi chọn xe'',
        ''cach-doc-thong-so-momen-xoan-va-cong-suat'',
        N''Hiểu đúng hai thông số cơ bản sẽ giúp bạn chọn xe phù hợp hơn với nhu cầu đi phố, đi tỉnh hay chở đủ tải.'',
        N''<p>Công suất thể hiện khả năng duy trì tốc độ và vận hành ở dải tua cao, trong khi mô-men xoắn cho biết lực kéo và cảm giác bốc ở dải tua thấp.</p><p>Người đi phố nhiều thường cảm nhận lợi ích của mô-men xoắn rõ hơn, đặc biệt khi chở đủ tải hoặc leo dốc. Người chạy đường dài sẽ quan tâm thêm đến độ êm, cách hộp số sang số và mức tiêu hao nhiên liệu.</p><p>Đọc thông số đúng giúp bạn tránh mua xe theo cảm tính và so sánh thực tế hơn giữa các mẫu xe khác nhau.</p>'',
        ''https://placehold.co/1200x800/1e3a8a/f8fafc?text=Cong+suat+Mo-men'',
        NULL,
        c.id,
        ''published'',
        0,
        ''2026-04-16T07:30:00'',
        91,
        0
    FROM dbo.ArticleCategories c
    WHERE c.slug = ''dong-co'';
END;
';

EXEC sys.sp_executesql @ArticleSeedSql;
