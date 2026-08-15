import { LockOutlined, UserOutlined } from '@ant-design/icons';
import { useMutation } from '@tanstack/react-query';
import { Alert, Button, Card, Form, Input, Typography } from 'antd';
import { useNavigate } from 'react-router-dom';
import { loginAdmin, type AdminLoginCommand } from '../../api/auth';

export function LoginPage() {
  const navigate = useNavigate();
  const loginMutation = useMutation({
    mutationFn: loginAdmin,
    onSuccess: () => {
      navigate('/system/users', { replace: true });
    },
  });

  const errorMessage = loginMutation.error instanceof Error ? loginMutation.error.message : '登录失败，请稍后重试';

  return (
    <main
      style={{
        minHeight: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        background: '#f3f6fb',
        padding: 24,
      }}
    >
      <Card style={{ width: '100%', maxWidth: 420 }} styles={{ body: { padding: 32 } }}>
        <div style={{ marginBottom: 28, textAlign: 'center' }}>
          <Typography.Title level={2} style={{ marginBottom: 8 }}>
            智能药房 SaaS
          </Typography.Title>
          <Typography.Text type="secondary">管理后台</Typography.Text>
        </div>

        {loginMutation.isError ? (
          <Alert
            showIcon
            type="error"
            message={errorMessage}
            style={{ marginBottom: 16 }}
          />
        ) : null}

        <Form<AdminLoginCommand>
          layout="vertical"
          requiredMark={false}
          initialValues={{ tenantCode: 'default' }}
          onFinish={(values) => loginMutation.mutate(values)}
        >
          <Form.Item
            label="租户编码"
            name="tenantCode"
            rules={[{ required: true, message: '请输入租户编码' }]}
          >
            <Input autoComplete="organization" placeholder="default" />
          </Form.Item>

          <Form.Item
            label="用户名"
            name="username"
            rules={[{ required: true, message: '请输入用户名' }]}
          >
            <Input
              autoComplete="username"
              placeholder="请输入用户名"
              prefix={<UserOutlined />}
            />
          </Form.Item>

          <Form.Item
            label="密码"
            name="password"
            rules={[{ required: true, message: '请输入密码' }]}
          >
            <Input.Password
              autoComplete="current-password"
              placeholder="请输入密码"
              prefix={<LockOutlined />}
            />
          </Form.Item>

          <Button
            block
            type="primary"
            htmlType="submit"
            loading={loginMutation.isPending}
          >
            登录
          </Button>
        </Form>
      </Card>
    </main>
  );
}
