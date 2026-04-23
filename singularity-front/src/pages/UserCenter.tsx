import { useEffect, useState, useCallback } from 'react'
import { Card, Button, Table, Modal, Form, InputNumber, Row, Col, Tag, Space, Typography, message } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useAuth } from '../contexts/AuthContext'
import { userApi } from '../api/user'
import { orderApi } from '../api/order'
import type { UserDetail, Order } from '../api/types'

const { Title, Text } = Typography

export default function UserCenter() {
  const { user } = useAuth()
  const [userDetail, setUserDetail] = useState<UserDetail | null>(null)
  const [loadingUser, setLoadingUser] = useState(false)

  const [orders, setOrders] = useState<Order[]>([])
  const [orderTotal, setOrderTotal] = useState(0)
  const [orderLoading, setOrderLoading] = useState(false)
  const [page, setPage] = useState(1)
  const [pageSize] = useState(10)

  const [rechargeOpen, setRechargeOpen] = useState(false)
  const [recharging, setRecharging] = useState(false)
  const [rechargeForm] = Form.useForm()

  const fetchUserDetail = useCallback(async () => {
    if (!user) return
    setLoadingUser(true)
    try {
      const res = await userApi.list()
      if (res.success && res.data) {
        const detail = res.data.find((u) => u.id === user.id)
        if (detail) setUserDetail(detail)
      }
    } catch {
      message.error('获取用户信息失败')
    } finally {
      setLoadingUser(false)
    }
  }, [user])

  const fetchOrders = useCallback(async (p: number, s: number) => {
    if (!user) return
    setOrderLoading(true)
    try {
      const res = await orderApi.list({ actorId: String(user.id), page: p, size: s })
      if (res.success && res.data) {
        setOrders(res.data.content)
        setOrderTotal(res.data.totalElements)
      } else {
        message.error(res.error?.message ?? '获取订单列表失败')
      }
    } catch {
      message.error('获取订单列表失败')
    } finally {
      setOrderLoading(false)
    }
  }, [user])

  useEffect(() => {
    fetchUserDetail()
    fetchOrders(0, pageSize)
  }, [fetchUserDetail, fetchOrders, pageSize])

  const handleRecharge = async () => {
    if (!user) return
    const values = await rechargeForm.validateFields()
    setRecharging(true)
    try {
      const res = await userApi.recharge(user.id, { amount: values.amount })
      if (res.success) {
        message.success('充值成功')
        setRechargeOpen(false)
        rechargeForm.resetFields()
        fetchUserDetail()
      } else {
        message.error(res.error?.message ?? '充值失败')
      }
    } catch {
      message.error('充值失败，请稍后重试')
    } finally {
      setRecharging(false)
    }
  }

  const handleTableChange = (pagination: { current?: number; pageSize?: number }) => {
    const p = pagination.current ?? 1
    setPage(p)
    fetchOrders(p - 1, pagination.pageSize ?? pageSize)
  }

  const orderColumns: ColumnsType<Order> = [
    { title: '订单号', dataIndex: 'orderId', render: (v: string) => v.slice(0, 12) + '...' },
    { title: '商品', dataIndex: 'slotId' },
    {
      title: '状态',
      dataIndex: 'status',
      render: (v: string) => {
        if (v === 'PAID') return <Tag color="success">成功</Tag>
        if (v === 'CANCELLED') return <Tag color="error">失败</Tag>
        return <Tag color="processing">处理中</Tag>
      },
    },
    { title: '创建时间', dataIndex: 'createTime' },
  ]

  return (
    <div>
      <Title level={4}>用户中心</Title>
      <Row gutter={[16, 16]}>
        <Col xs={24} md={12}>
          <Card title="用户信息" loading={loadingUser}>
            <Space direction="vertical">
              <Text>用户名: {userDetail?.username ?? user?.username}</Text>
              <Text>昵称: {userDetail?.nickname ?? '-'}</Text>
              <Text>
                角色:{' '}
                <Tag color={userDetail?.role === 'admin' ? 'red' : 'blue'}>
                  {userDetail?.role ?? user?.role}
                </Tag>
              </Text>
            </Space>
          </Card>
        </Col>
        <Col xs={24} md={12}>
          <Card
            title="账户余额"
            extra={
              <Button type="primary" size="small" onClick={() => setRechargeOpen(true)}>
                充值
              </Button>
            }
            loading={loadingUser}
          >
            <Text style={{ fontSize: 24, fontWeight: 'bold' }}>
              ¥{(userDetail?.balance ?? 0).toFixed(2)}
            </Text>
          </Card>
        </Col>
      </Row>

      <Card title="我的订单" style={{ marginTop: 16 }}>
        <Table
          rowKey="orderId"
          columns={orderColumns}
          dataSource={orders}
          loading={orderLoading}
          pagination={{ current: page, pageSize, total: orderTotal }}
          onChange={handleTableChange}
        />
      </Card>

      <Modal
        title="充值余额"
        open={rechargeOpen}
        onOk={handleRecharge}
        onCancel={() => {
          setRechargeOpen(false)
          rechargeForm.resetFields()
        }}
        confirmLoading={recharging}
        okText="确认充值"
        cancelText="取消"
      >
        <Form form={rechargeForm} layout="vertical">
          <Form.Item
            name="amount"
            label="充值金额"
            rules={[{ required: true, message: '请输入充值金额' }]}
          >
            <InputNumber min={0.01} precision={2} style={{ width: '100%' }} placeholder="请输入金额" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}
