export function getErrorMessage(error: unknown, fallback = '操作失败'): string {
  if (error && typeof error === 'object') {
    const err = error as {
      response?: { data?: { message?: string } };
      message?: string;
    };
    if (err.response?.data?.message) {
      return err.response.data.message;
    }
    if (err.message) {
      return err.message;
    }
  }
  return fallback;
}
