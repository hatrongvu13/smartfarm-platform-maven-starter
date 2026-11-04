/**
 * Mọi chữ hiển thị của giao diện (W5). Một chỗ duy nhất để sửa câu chữ và giữ thuật ngữ thống nhất.
 * `npm run check:i18n` báo lỗi nếu chữ tiếng Việt xuất hiện ở file khác trong src/.
 *
 * Thuật ngữ:
 *   vai trò (không "role")   · quyền (không "permission") · tổ chức (không "tenant")
 *   xác thực hai bước (không "2FA"/"TOTP" ở chữ hiển thị chính) · quản trị hệ thống (không "super admin")
 * Mã kỹ thuật (FARM_WORKER, farm:farm:read, SF-FARM-010) giữ nguyên: đó là dữ liệu, không phải câu chữ.
 *
 * Câu có tham số là hàm. Không thêm thư viện i18n: thêm ngôn ngữ thì thêm một file cùng kiểu `Messages`.
 */
export const vi = {
  app: {
    name: 'FarmVerse',
    titleSuffix: ' · FarmVerse',
    loading: 'Đang tải…',
  },

  common: {
    cancel: 'Hủy',
    close: 'Đóng',
    save: 'Lưu',
    saved: 'Đã lưu.',
    create: 'Tạo',
    add: 'Thêm',
    edit: 'Sửa',
    delete: 'Xóa',
    retry: 'Thử lại',
    actions: 'Thao tác',
    code: 'Mã',
    name: 'Tên',
    description: 'Mô tả',
    address: 'Địa chỉ',
    email: 'Email',
    errorCode: (code: string) => `Mã lỗi: ${code}`,
    system: 'hệ thống',
  },

  /** Nhãn trạng thái từ API; giá trị lạ hiện nguyên mã. */
  status: {
    ACTIVE: 'Đang hoạt động',
    SUSPENDED: 'Tạm khóa',
    DISABLED: 'Đã rời tổ chức',
    ARCHIVED: 'Đã lưu trữ',
  } as Record<string, string>,

  auth: {
    loginTitle: 'Đăng nhập FarmVerse',
    loginSub: 'Bạn sẽ được chuyển sang trang đăng nhập của hệ thống.',
    login: 'Đăng nhập',
    logout: 'Đăng xuất',
  },

  nav: {
    label: 'Điều hướng chính',
    skipToContent: 'Bỏ qua đến nội dung chính',
    overview: 'Tổng quan',
    farms: 'Trang trại',
    users: 'Người dùng',
    authorization: 'Phân quyền',
    farmSelector: 'Trang trại',
    farmSelectorLabel: 'Mã trang trại',
  },

  notifications: {
    title: 'Thông báo',
    description: 'Đồng bộ quyền, thao tác quản trị và lỗi hệ thống.',
    button: (unread: number) => (unread ? `Thông báo, ${unread} chưa đọc` : 'Thông báo'),
    empty: 'Chưa có thông báo.',
    clearAll: 'Xóa tất cả',
    details: (count: number) => `Xem chi tiết (${count})`,
  },

  errors: {
    network: { title: 'Không kết nối được máy chủ', detail: 'Kiểm tra kết nối mạng rồi thử lại.' },
    validation: { title: 'Dữ liệu chưa hợp lệ' },
    auth: { title: 'Phiên đăng nhập đã hết', detail: 'Hãy đăng nhập lại để tiếp tục.' },
    forbidden: { title: 'Bạn không có quyền làm thao tác này', detail: 'Quyền có thể vừa được thay đổi. Liên hệ quản trị viên nếu bạn cần quyền này.' },
    notFound: { title: 'Không tìm thấy' },
    conflict: { title: 'Dữ liệu vừa thay đổi', detail: 'Hãy tải lại rồi thử lại.' },
    rule: { title: 'Không thực hiện được thao tác' },
    rateLimit: { title: 'Bạn thao tác quá nhanh', detail: 'Đợi vài giây rồi thử lại.' },
    unavailable: { title: 'Hệ thống tạm thời không phản hồi', detail: 'Thường tự hết sau ít phút. Hãy thử lại.' },
    server: { title: 'Đã có lỗi phía máy chủ', detail: 'Dữ liệu của bạn chưa bị thay đổi. Hãy thử lại sau.' },
  },

  crash: {
    title: 'Không thể hiển thị màn hình',
    detail: 'Dữ liệu của bạn chưa bị thay đổi. Hãy tải lại trang hoặc quay về tổng quan.',
    reload: 'Tải lại',
    home: 'Về tổng quan',
  },

  forbiddenPage: {
    title: 'Thiếu quyền',
    sub: 'Bạn không có quyền truy cập trang này.',
    needs: 'Cần quyền',
    contact: 'Liên hệ quản trị viên nếu bạn cần truy cập.',
  },

  dashboard: {
    title: 'Tổng quan',
    greeting: (name: string) => `Xin chào, ${name}`,
    session: 'Phiên đăng nhập',
    organization: 'Tổ chức',
    roles: 'Vai trò',
    permissions: (count: number) => `Quyền (${count})`,
    noPermissions: 'Chưa có quyền nào. Quản trị hệ thống vẫn vào được mọi trang.',
    goTo: 'Đi tới',
    goFarms: 'Trang trại và chuồng',
    goProfile: 'Hồ sơ của tôi',
  },

  farms: {
    title: 'Trang trại',
    sub: 'Trang trại và chuồng của tổ chức',
    list: 'Danh sách',
    loading: 'Đang tải trang trại…',
    empty: 'Chưa có trang trại nào.',
    emptyCanCreate: 'Chưa có trang trại nào. Tạo trang trại đầu tiên ở khung bên cạnh.',
    summary: (barns: number, capacity: number) => `${barns} chuồng · sức chứa ${capacity}`,
    select: 'Chọn',
    selected: 'Đang chọn',
    create: 'Tạo trang trại',
    codeHint: 'viết thường, ví dụ green-valley',
    barnsOf: (farm: string) => `Chuồng của ${farm}`,
    noBarns: 'Chưa có chuồng nào.',
    noBarnsCanAdd: 'Chưa có chuồng nào. Thêm chuồng ở khung bên cạnh.',
    capacity: 'Sức chứa',
    addBarn: 'Thêm chuồng',
    barnCodeHint: 'VIẾT HOA, ví dụ B-1',
    deleteBarnLabel: (code: string) => `Xóa chuồng ${code}`,
    confirmDeleteBarn: (code: string, barn: string, farm: string) => ({
      title: `Xóa chuồng ${code}?`,
      body: `Chuồng "${barn}" sẽ bị gỡ khỏi ${farm}.`,
      confirmLabel: 'Xóa chuồng',
    }),
  },

  users: {
    title: 'Người dùng',
    sub: (org: string, total?: number) => `Tổ chức ${org}${total === undefined ? '' : ` · ${total} người`}`,
    list: 'Danh sách',
    loading: 'Đang tải người dùng…',
    empty: 'Tổ chức chưa có người dùng nào.',
    user: 'Người dùng',
    status: 'Trạng thái',
    role: 'Vai trò',
    noRole: '(không có vai trò)',
    roleOf: (email: string) => `Vai trò của ${email}`,
    locked: 'Khóa tạm thời',
    suspend: 'Tạm khóa',
    suspendLabel: (email: string) => `Tạm khóa ${email}`,
    confirmSuspend: (email: string) => ({
      title: `Tạm khóa ${email}?`,
      body: 'Người này không đăng nhập được và không làm mới được phiên cho tới khi được mở lại.',
      confirmLabel: 'Tạm khóa',
    }),
    reactivate: 'Mở lại',
    unlock: 'Mở khóa',
    resetMfa: 'Đặt lại xác thực hai bước',
    resetMfaLabel: (email: string) => `Đặt lại xác thực hai bước của ${email}`,
    confirmResetMfa: (email: string) => ({
      title: `Đặt lại xác thực hai bước của ${email}?`,
      body: 'Dùng khi người dùng mất thiết bị tạo mã. Mã hiện tại và mọi mã khôi phục mất hiệu lực; họ phải đăng ký lại.',
      confirmLabel: 'Đặt lại',
    }),
    previous: '‹ Trước',
    next: 'Sau ›',
    page: (current: number, total: number) => `Trang ${current} / ${total}`,
    add: 'Thêm người dùng',
    displayName: 'Tên hiển thị',
    password: 'Mật khẩu',
    passwordHint: 'Chỉ cần khi email chưa có tài khoản (ít nhất 12 ký tự)',
  },

  authorization: {
    title: 'Phân quyền',
    sub: 'Vai trò của tổ chức; mỗi vai trò là một nhóm quyền',
    roles: 'Vai trò',
    loading: 'Đang tải vai trò…',
    empty: 'Chưa có vai trò nào.',
    permissionCount: (name: string, count: number) => `${name} · ${count} quyền`,
    editLabel: (code: string) => `Sửa vai trò ${code}`,
    deleteLabel: (code: string) => `Xóa vai trò ${code}`,
    confirmDelete: (code: string) => ({
      title: `Xóa vai trò ${code}?`,
      body: 'Người dùng đang có vai trò này sẽ mất các quyền của nó ở lần đăng nhập hoặc làm mới phiên kế tiếp.',
      confirmLabel: 'Xóa vai trò',
    }),
    create: 'Tạo vai trò',
    edit: (code: string) => `Sửa vai trò ${code}`,
    codeHint: 'CHỮ HOA, ví dụ FARM_WORKER',
    permissions: 'Quyền',
  },

  profile: {
    title: 'Hồ sơ của tôi',
    info: 'Thông tin',
    loading: 'Đang tải hồ sơ…',
    displayName: 'Tên hiển thị',
    locale: 'Ngôn ngữ',
    timeZone: 'Múi giờ',
    changePassword: 'Đổi mật khẩu',
    currentPassword: 'Mật khẩu hiện tại',
    newPassword: 'Mật khẩu mới',
    newPasswordHint: 'Ít nhất 12 ký tự',
    passwordChanged: 'Đã đổi mật khẩu.',
  },

  totp: {
    title: 'Xác thực hai bước',
    on: 'Đang bật',
    off: 'Đang tắt',
    loading: 'Đang tải trạng thái…',
    codeLabel: 'Mã 6 số từ ứng dụng',
    recoveryTitle: 'Lưu các mã khôi phục này ở nơi an toàn. Sẽ không hiện lại.',
    recoveryHelp: 'Mỗi mã dùng được một lần, để đăng nhập khi mất điện thoại.',
    recoverySaved: 'Tôi đã lưu',
    apps: 'Dùng Google Authenticator, Microsoft Authenticator, 1Password… để sinh mã đăng nhập.',
    enable: 'Bật xác thực hai bước',
    step1: '1. Trong ứng dụng, chọn "Thêm tài khoản" → "Nhập khóa", dán khóa sau (hoặc mở liên kết trên điện thoại):',
    step2: '2. Nhập mã ứng dụng hiện ra:',
    confirmEnable: 'Xác nhận và bật',
    remaining: (count: number) => `Còn ${count} mã khôi phục chưa dùng.`,
    manageHelp: 'Nhập mã hiện tại để sinh lại mã khôi phục (bộ cũ mất hiệu lực) hoặc để tắt xác thực hai bước.',
    regenerate: 'Sinh lại mã khôi phục',
    confirmRegenerate: {
      title: 'Sinh lại mã khôi phục?',
      body: 'Bộ mã cũ mất hiệu lực ngay. Hãy lưu bộ mã mới.',
      confirmLabel: 'Sinh lại',
    },
    disable: 'Tắt xác thực hai bước',
    confirmDisable: {
      title: 'Tắt xác thực hai bước?',
      body: 'Đăng nhập sẽ chỉ còn mật khẩu. Các mã khôi phục hiện tại mất hiệu lực.',
      confirmLabel: 'Tắt',
    },
  },
}

export type Messages = typeof vi

/** Nhãn tiếng Việt của một trạng thái API; giá trị chưa biết hiện nguyên mã thay vì để trống. */
export const statusLabel = (status?: string) => (status ? vi.status[status] ?? status : '')
