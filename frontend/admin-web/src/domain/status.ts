export function statusTone(status: string | null | undefined) {
  if (!status) return 'neutral';
  if (['APPROVED', 'COMPLETED', 'AUDIT_PASSED', 'RECHECKED', 'PASSED', 'SUCCESS', 'SENT', 'OK', 'SIGNED'].includes(status)) return 'success';
  if (['REJECTED', 'AUDIT_FAILED', 'FAILED', 'DEAD', 'CANCELLED', 'TERMINATED', 'ERROR'].includes(status)) return 'danger';
  if (['PENDING', 'CREATED', 'BOUND', 'DECOCTING', 'OCCUPIED', 'WORKING', 'NEW', 'RETRYING', 'PACKED', 'SHIPPED', 'IN_TRANSIT'].includes(status)) return 'warning';
  if (['DECOCTED', 'IDLE'].includes(status)) return 'success';
  return 'neutral';
}
