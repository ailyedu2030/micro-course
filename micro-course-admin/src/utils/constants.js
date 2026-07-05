/**
 * 微课管理平台 · 全局常量
 *
 * ===== 密码规则 =====
 * 统一密码校验规则，所有涉及密码的场景（登录/注册/修改密码）必须引用此常量
 */
export const PASSWORD_RULES = {
  /** 最小长度 */
  min: 8,
  /** 最大长度 */
  max: 32,
  /** 正则：必须包含字母 + 数字 */
  pattern: /^(?=.*[A-Za-z])(?=.*\d)/,
  /** 正则未通过提示 */
  patternMessage: '密码需包含字母和数字'
}

/** 密码规则对应的表单校验规则（可直接 spread 到 el-form rules） */
export const PASSWORD_VALIDATORS = [
  { required: true, message: '请输入密码', trigger: 'blur' },
  { min: PASSWORD_RULES.min, max: PASSWORD_RULES.max, message: `密码长度为 ${PASSWORD_RULES.min}-${PASSWORD_RULES.max} 个字符`, trigger: 'blur' },
  { pattern: PASSWORD_RULES.pattern, message: PASSWORD_RULES.patternMessage, trigger: 'blur' }
]

/** 用户名规则 */
export const USERNAME_RULES = {
  min: 2,
  max: 50
}

/** 用户名对应的表单校验规则 */
export const USERNAME_VALIDATORS = [
  { required: true, message: '请输入用户名', trigger: 'blur' },
  { min: USERNAME_RULES.min, max: USERNAME_RULES.max, message: `用户名长度为 ${USERNAME_RULES.min}-${USERNAME_RULES.max} 个字符`, trigger: 'blur' },
  { pattern: /^\S+$/, message: '用户名不能包含空格', trigger: 'blur' }
]
