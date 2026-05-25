-- Auto-confirm legacy deposits (no longer require branch approval)
UPDATE Deposits SET status = 'Confirmed' WHERE status = 'Pending';

UPDATE Transactions SET status = 'Completed'
WHERE reference_type = 'Deposit'
  AND status = 'Pending'
  AND reference_id IN (SELECT id FROM Deposits WHERE status = 'Confirmed');
