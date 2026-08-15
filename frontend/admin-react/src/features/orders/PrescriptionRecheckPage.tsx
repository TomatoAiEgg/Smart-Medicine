import { Button, Card, Form, Input, Space } from 'antd';
import { PageHeader } from '../../components/PageHeader';

interface PrescriptionRecheckForm {
  prescriptionNo: string;
  dispenser?: string;
  reviewer?: string;
  waterPailNo?: string;
}

export function PrescriptionRecheckPage() {
  const [form] = Form.useForm<PrescriptionRecheckForm>();

  return (
    <section className="query-page">
      <PageHeader
        title="处方复核"
        subtitle="按处方号完成调剂员、复核员和加水桶号的作业登记。"
      />
      <Card size="small" className="query-page__filters">
        <Form<PrescriptionRecheckForm>
          form={form}
          layout="vertical"
          requiredMark={false}
          style={{ maxWidth: 720 }}
        >
          <Form.Item
            label="处方号"
            name="prescriptionNo"
            rules={[{ required: true, message: '请输入处方号' }]}
          >
            <Input placeholder="请输入或扫码处方号" allowClear />
          </Form.Item>
          <Form.Item label="调剂员" name="dispenser">
            <Input placeholder="请输入调剂员工号或姓名" allowClear />
          </Form.Item>
          <Form.Item label="复核员" name="reviewer">
            <Input placeholder="请输入复核员工号或姓名" allowClear />
          </Form.Item>
          <Form.Item label="加水桶号" name="waterPailNo">
            <Input placeholder="请输入加水桶号" allowClear />
          </Form.Item>
          <Form.Item>
            <Space wrap>
              <Button type="primary" htmlType="submit">
                提交复核
              </Button>
              <Button htmlType="reset">重置</Button>
            </Space>
          </Form.Item>
        </Form>
      </Card>
    </section>
  );
}
