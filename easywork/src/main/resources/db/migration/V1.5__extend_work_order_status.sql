-- V1.5: Extend work order status to support SCRAPPED terminal state
-- and document all supported status values for production/inspection/transport/andon order types
-- No column type change needed; work_orders.status is VARCHAR and accepts any string value

COMMENT ON COLUMN work_orders.status IS
  'Work order status. Supported values:
   PRODUCTION type: NOT_STARTED | STARTED | REPORTED | INSPECT_PASSED | INSPECT_FAILED | SCRAPPED | COMPLETED
   INSPECTION type: NOT_STARTED | STARTED | COMPLETED
   TRANSPORT  type: NOT_STARTED | STARTED | COMPLETED
   ANDON      type: NOT_STARTED | STARTED | COMPLETED';
