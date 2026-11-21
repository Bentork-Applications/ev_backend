# Frontend API Integration Walkthrough — Bentork CRM

This document provides a comprehensive integration guide for the frontend team to interact with the newly implemented and updated CRM APIs in the backend.

---

## Table of Contents
1. [General Setup & Authentication](#1-general-setup--authentication)
2. [Campaign Update & 5-Round Follow-up System](#2-campaign-update--5-round-follow-up-system)
3. [Lead Bulk Operations](#3-lead-bulk-operations)
   - [3.1 Bulk Assign](#31-bulk-assign)
   - [3.2 Bulk Tag](#32-bulk-tag)
   - [3.3 CSV Import](#33-csv-import)
4. [Contacts: Load All (`GET /all`)](#4-contacts-load-all-get-all)
5. [Quotations: Load All (`GET /all`)](#5-quotations-load-all-get-all)
6. [Activities & Follow-up Workflow](#6-activities--follow-up-workflow)
   - [6.1 All Activities](#61-all-activities)
   - [6.2 Follow-up Buckets](#62-follow-up-buckets)
   - [6.3 KPI Summary Cards](#63-kpi-summary-cards)
   - [6.4 Reschedule Follow-up](#64-reschedule-follow-up)
   - [6.5 Complete Activity](#65-complete-activity)
7. [Complete Frontend Integration Code Examples](#7-complete-frontend-integration-code-examples)
8. [API Route Quick Reference Table](#8-api-route-quick-reference-table)

---

## 1. General Setup & Authentication

### Base URL
- **Local Development:** `http://localhost:8080`
- **Production Server:** `https://<your-backend-domain>`

### HTTP Headers
All CRM endpoints require `SALES_ADMIN` or `ADMIN` roles. Attach the JWT token to every request:
```http
Authorization: Bearer <your_jwt_token>
Content-Type: application/json
```
*(For CSV file uploads, set `Content-Type: multipart/form-data` or allow your HTTP client like Axios to set the boundary automatically).*

---

## 2. Campaign Update & 5-Round Follow-up System

### Problem Solved
Previously, saving edits or the 5 rounds of sequential follow-up campaigns triggered an HTTP `405 Method Not Allowed`. The backend now provides unified update routing supporting both `PUT` and `POST`, and transactionally saves the campaign metadata alongside all 5 cascading rounds.

### Endpoints
- `PUT /api/crm/campaigns/{id}` *(Primary)*
- `POST /api/crm/campaigns/{id}/update` *(Alias fallback)*
- `PUT /api/crm/follow-up/campaigns/{campaignId}` *(Direct follow-up controller route)*

### Request Payload (`UpdateCampaignDTO`)
```json
{
  "name": "Q4 Commercial EV Charging Outreach",
  "channel": "WHATSAPP",
  "type": "FOLLOW_UP",
  "status": "DRAFT",
  "description": "5-round cascading follow-up for commercial fleet prospects",
  "objective": "Schedule demo meetings for 60kW DC fast chargers",
  "scheduledDate": "2026-12-01",
  "startDate": "2026-12-01",
  "endDate": "2026-12-31",
  "timezone": "Asia/Kolkata",
  "sendMode": "MANUAL",
  "ownerAdminId": 1,
  "rounds": [
    {
      "roundNumber": 1,
      "channel": "WHATSAPP",
      "subject": "Initial Intro & Product Catalog",
      "messageBody": "Hi {{contactName}}, thanks for connecting with Bentork EV! Here is our latest commercial charger catalog.",
      "templateName": "Round 1 Intro",
      "plannedDate": "2026-12-01",
      "plannedTime": "10:30:00",
      "sendMode": "MANUAL",
      "status": "CONFIGURED"
    },
    {
      "roundNumber": 2,
      "channel": "WHATSAPP",
      "subject": "ROI & Fleet Charging Case Study",
      "messageBody": "Hi {{contactName}}, see how logistics companies reduced fleet downtime by 35% using our fast chargers.",
      "templateName": "Round 2 Case Study",
      "plannedDate": "2026-12-04",
      "plannedTime": "11:00:00",
      "sendMode": "MANUAL",
      "status": "CONFIGURED"
    },
    {
      "roundNumber": 3,
      "channel": "WHATSAPP",
      "subject": "Live Demo Invitation",
      "messageBody": "Hi {{contactName}}, we are running live depot charging demonstrations this week. Would you like a slot?",
      "templateName": "Round 3 Demo",
      "plannedDate": "2026-12-08",
      "plannedTime": "14:00:00",
      "sendMode": "MANUAL",
      "status": "CONFIGURED"
    },
    {
      "roundNumber": 4,
      "channel": "WHATSAPP",
      "subject": "Year-End Commercial Pricing",
      "messageBody": "Special CAPEX incentives available this month for bulk charger orders. Reply to review custom quotation terms.",
      "templateName": "Round 4 Offer",
      "plannedDate": "2026-12-12",
      "plannedTime": "10:00:00",
      "sendMode": "MANUAL",
      "status": "CONFIGURED"
    },
    {
      "roundNumber": 5,
      "channel": "WHATSAPP",
      "subject": "Final Follow-up & Feedback",
      "messageBody": "Hi {{contactName}}, should we keep this requirement active for your team or reconnect next quarter?",
      "templateName": "Round 5 Closeout",
      "plannedDate": "2026-12-16",
      "plannedTime": "16:00:00",
      "sendMode": "MANUAL",
      "status": "CONFIGURED"
    }
  ]
}
```

### Response (`CampaignResponse`)
```json
{
  "id": 10,
  "name": "Q4 Commercial EV Charging Outreach",
  "type": "FOLLOW_UP",
  "channel": "WHATSAPP",
  "status": "DRAFT",
  "totalRounds": 5,
  "completedRounds": 0,
  "ownerAdminId": 1,
  "ownerAdminName": "Sales Manager",
  "rounds": [
    {
      "id": 101,
      "roundNumber": 1,
      "channel": "WHATSAPP",
      "subject": "Initial Intro & Product Catalog",
      "messageBody": "Hi {{contactName}}, thanks for connecting with Bentork EV! Here is our latest commercial charger catalog.",
      "plannedDate": "2026-12-01",
      "plannedTime": "10:30:00",
      "sendMode": "MANUAL",
      "status": "CONFIGURED"
    }
  ],
  "createdAt": "2026-11-20T10:00:00",
  "updatedAt": "2026-11-20T17:15:00"
}
```

---

## 3. Lead Bulk Operations

### 3.1 Bulk Assign
Assigns multiple selected leads to a specific sales representative / admin.
- **Method**: `POST` (or `PUT`)
- **Endpoints**: `/api/crm/leads/bulk-assign` *(or `/api/crm/leads/bulk/assign`)*
- **Payload**:
```json
{
  "leadIds": [101, 102, 103, 104],
  "targetOwnerAdminId": 5
}
```
> **Frontend Alias Support:** The backend also accepts `ownerId`, `adminId`, or `assignedTo` in place of `targetOwnerAdminId`, and `ids` in place of `leadIds`.

#### Response
```json
{
  "totalRequested": 4,
  "successCount": 4,
  "failureCount": 0,
  "errors": []
}
```

---

### 3.2 Bulk Tag
Appends one or more tags to selected leads without removing existing tags.
- **Method**: `POST` (or `PUT`)
- **Endpoints**: `/api/crm/leads/bulk-tag` *(or `/api/crm/leads/bulk/tag`)*
- **Payload (Array of tags or single string)**:
```json
{
  "leadIds": [101, 102, 103],
  "tags": ["FleetOperator", "VIP", "HotLead"]
}
```
*(You can also send a single string: `{ "leadIds": [101, 102], "tag": "FleetOperator" }`)*

#### Response
```json
{
  "totalRequested": 3,
  "successCount": 3,
  "failureCount": 0,
  "errors": []
}
```

---

### 3.3 CSV Import
Imports leads in bulk from a user-uploaded CSV file. Automatically parses and creates matching Companies and Contacts when provided.
- **Method**: `POST`
- **Endpoints**: `/api/crm/leads/import` *(or `/api/crm/leads/csv-import`, `/api/crm/leads/import-csv`)*
- **Content-Type**: `multipart/form-data`
- **File Key**: `file` (or `csv`, `leadsFile`)

#### Flexible CSV Column Headers (Case and Symbol Insensitive)
| Target Field | Accepted Column Headers in CSV |
| :--- | :--- |
| **Title / Lead Name** | `title`, `lead name`, `name`, `lead title`, `subject` |
| **Phone** | `phone`, `contact phone`, `mobile`, `phonenumber`, `contact no` |
| **Email** | `email`, `contact email`, `email address`, `mail` |
| **Company** | `company`, `company name`, `organization`, `firm`, `account` |
| **Contact Person** | `contact name`, `name`, `contact`, `person`, `full name` |
| **City** | `city`, `location`, `town` |
| **State** | `state`, `province`, `region` |
| **Estimated Value** | `estimated value`, `value`, `amount`, `budget`, `deal value` |
| **Notes** | `notes`, `note`, `remarks`, `comment`, `description` |
| **Lead Source** | `source`, `lead source`, `channel` |

#### Response
```json
{
  "totalRows": 25,
  "importedCount": 24,
  "skippedCount": 1,
  "errors": [
    "Row 15: insufficient columns"
  ]
}
```

---

## 4. Contacts: Load All (`GET /all`)

### Problem Solved
Calling `GET /api/crm/contacts/all` previously collided with `GET /api/crm/contacts/{id}` where the backend tried to parse the string `"all"` as a numeric ID. Route constraints have been added (`/{id:[0-9]+}`), and `/all` is now fully operational.

### Endpoints
- `GET /api/crm/contacts/all` *(Complete list)*
- `GET /api/crm/contacts` *(Root alias)*

### Query Parameters
| Parameter | Type | Default | Description |
| :--- | :--- | :--- | :--- |
| `paged` | `boolean` | `false` | When `true`, returns a paginated structure (`PagedResponse`). When omitted or `false`, returns a flat array `ContactResponse[]`. |
| `page` | `number` | `0` | Page index (0-based) |
| `size` | `number` | `20` | Page size |
| `companyId`| `number` | `null` | Filter contacts belonging to a specific company |
| `search` | `string` | `null` | Search contacts by name |

### Response Example (Flat Mode: `paged=false`)
```json
[
  {
    "id": 12,
    "companyId": 4,
    "companyName": "Mahindra Logistics",
    "name": "Rajesh Kumar",
    "designation": "Fleet Procurement Head",
    "phone": "+919876543210",
    "email": "rajesh.k@mahindralogistics.com",
    "isPrimary": true,
    "createdAt": "2026-11-15T11:20:00"
  }
]
```

---

## 5. Quotations: Load All (`GET /all`)

### Endpoints
- `GET /api/crm/quotations/all` *(Complete list)*
- `GET /api/crm/quotations` *(Root alias)*
- `GET /api/crm/quotations/company/{companyId}` *(Filter by company)*
- `GET /api/crm/quotations/opportunity/{opportunityId}` *(Filter by opportunity)*

### Query Parameters
- `paged`: `true` for pagination (`PagedResponse<QuotationResponse>`), `false` for raw list (`QuotationResponse[]`).
- `page`: default `0`
- `size`: default `20`

### Response Example
```json
[
  {
    "id": 105,
    "quotationNumber": "QT-2026-0042",
    "opportunityId": 18,
    "companyId": 4,
    "companyName": "Mahindra Logistics",
    "version": 1,
    "totalAmount": 1250000.00,
    "status": "SENT",
    "validUntil": "2026-12-31",
    "createdAt": "2026-11-18T14:30:00"
  }
]
```

---

## 6. Activities & Follow-up Workflow

This suite powers the **Activities / Follow-up** screen, including KPI metric cards, overdue/today/upcoming tabbed lists, rescheduling dialogs, and activity completion modals.

### 6.1 All Activities
- **Endpoint**: `GET /api/crm/activities/all` *(or `/api/crm/activities`)*
- **Query Parameters**:
  - `activityType` (or `type`): `CALL`, `MEETING`, `EMAIL`, `NOTE`
  - `status`: `pending`, `completed`
  - `callOutcome` (or `outcome`): `interested`, `callback`, `meeting_scheduled`, etc.
  - `adminId`: filter by sales rep
  - `myOnly`: `true` to filter strictly for logged-in user
  - `startDate`, `endDate`: `yyyy-MM-dd`
  - `search` (or `query`): search title, notes, contact name
  - `page`: default `0`, `size`: default `20`
  - `paged`: `true` (default) or `false` for flat array

---

### 6.2 Follow-up Buckets
Powers the tabbed follow-up lists (`Overdue`, `Today`, `Upcoming`, `Completed`, `All`).
- **Endpoint**: `GET /api/crm/activities/follow-ups` *(or `/api/crm/activities/followups`)*
- **Query Parameters**:
  - `bucket` (or `filter`):
    - `today`: Scheduled for today, status `pending` (sorted chronologically by time)
    - `overdue`: Past follow-up date/time, status `pending`
    - `upcoming`: Future date, status `pending`
    - `completed`: Completed follow-ups
    - `all`: All activities with follow-up dates
  - `myOnly`: `true` to view my follow-ups only
  - `page`: default `0`, `size`: default `20`

---

### 6.3 KPI Summary Cards
Retrieves live overview metrics for the top cards of the Activities page.
- **Endpoint**: `GET /api/crm/activities/summary` *(or `/api/crm/activities/stats`, `/overview`)*
- **Query Parameters**: `myOnly=true` (optional)
- **Response**:
```json
{
  "callsToday": 14,
  "totalCalls": 240,
  "meetingsToday": 3,
  "followUpsDueToday": 7,
  "overdueFollowUps": 2,
  "upcomingFollowUps": 19,
  "completedFollowUps": 85,
  "callOutcomes": {
    "interested": 35,
    "callback": 18,
    "meeting_scheduled": 9,
    "not_interested": 12,
    "no_answer": 42
  }
}
```

---

### 6.4 Reschedule Follow-up
Reschedules an activity date and time, auto-updates the parent Lead's next follow-up schedule, and logs an audit note.
- **Method**: `PUT` (or `POST`)
- **Endpoints**: `/api/crm/activities/{id}/reschedule` *(or `/api/crm/activities/reschedule/{id}`)*
- **Payload (`RescheduleActivityDTO`)**:
```json
{
  "newFollowUpDate": "2026-11-25",
  "newFollowUpTime": "14:30:00",
  "reason": "Client requested follow-up after board approval"
}
```
> **Frontend Date Format Flexibility:** The backend handles both `yyyy-MM-dd` and full ISO datetime strings (e.g., `2026-11-25T14:30:00.000Z`). You can also pass `followUpDate` or `date` as the field name.

---

### 6.5 Complete Activity
Marks an activity completed, stamps `completedAt`, and appends resolution notes.
- **Method**: `PUT` (or `POST`)
- **Endpoints**: `/api/crm/activities/{id}/complete` *(or `/api/crm/activities/complete/{id}`)*
- **Payload (`CompleteActivityDTO`)**:
```json
{
  "notes": "Spoke with technical team. Verified power availability at depot site.",
  "outcome": "meeting_scheduled"
}
```
*(Both `notes` and `outcome` are optional. Calling with `{}` or `null` marks the activity as completed).*

---

## 7. Complete Frontend Integration Code Examples

### 7.1 Axios Client Setup (`apiClient.ts`)
```typescript
import axios from 'axios';

export const apiClient = axios.create({
  baseURL: process.env.REACT_APP_API_BASE_URL || 'http://localhost:8080',
  headers: {
    'Content-Type': 'application/json'
  }
});

// Auto-attach JWT Bearer token
apiClient.interceptors.request.use((config) => {
  const token = localStorage.getItem('token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});
```

---

### 7.2 CRM API Module (`crmApi.ts`)
```typescript
import { apiClient } from './apiClient';

// ==================== CAMPAIGNS ====================
export interface CampaignRound {
  id?: number;
  roundNumber: number;
  channel: 'WHATSAPP' | 'EMAIL' | 'BOTH';
  subject?: string;
  messageBody?: string;
  templateName?: string;
  plannedDate?: string; // yyyy-MM-dd
  plannedTime?: string; // HH:mm:ss
  sendMode?: 'MANUAL' | 'AUTOMATIC';
  status?: string;
}

export interface UpdateCampaignPayload {
  name: string;
  channel: string;
  status?: string;
  description?: string;
  objective?: string;
  rounds?: CampaignRound[];
}

export const updateCampaign = async (id: number, payload: UpdateCampaignPayload) => {
  const { data } = await apiClient.put(`/api/crm/campaigns/${id}`, payload);
  return data;
};

export const getAllCampaigns = async () => {
  const { data } = await apiClient.get('/api/crm/campaigns/all');
  return data;
};

// ==================== LEADS BULK ====================
export const bulkAssignLeads = async (leadIds: number[], targetOwnerAdminId: number) => {
  const { data } = await apiClient.post('/api/crm/leads/bulk-assign', {
    leadIds,
    targetOwnerAdminId
  });
  return data;
};

export const bulkTagLeads = async (leadIds: number[], tags: string[]) => {
  const { data } = await apiClient.post('/api/crm/leads/bulk-tag', {
    leadIds,
    tags
  });
  return data;
};

export const importLeadsCsv = async (file: File) => {
  const formData = new FormData();
  formData.append('file', file);
  const { data } = await apiClient.post('/api/crm/leads/import', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  });
  return data;
};

// ==================== CONTACTS & QUOTATIONS ====================
export const getAllContacts = async (params?: { paged?: boolean; page?: number; size?: number; companyId?: number; search?: string }) => {
  const { data } = await apiClient.get('/api/crm/contacts/all', { params });
  return data;
};

export const getAllQuotations = async (params?: { paged?: boolean; page?: number; size?: number }) => {
  const { data } = await apiClient.get('/api/crm/quotations/all', { params });
  return data;
};

// ==================== ACTIVITIES & FOLLOW-UPS ====================
export const getActivitySummary = async (myOnly = false) => {
  const { data } = await apiClient.get('/api/crm/activities/summary', { params: { myOnly } });
  return data;
};

export const getFollowUps = async (bucket = 'today', page = 0, size = 20, myOnly = false) => {
  const { data } = await apiClient.get('/api/crm/activities/follow-ups', {
    params: { bucket, page, size, myOnly }
  });
  return data;
};

export const rescheduleActivity = async (id: number, newFollowUpDate: string, newFollowUpTime?: string, reason?: string) => {
  const { data } = await apiClient.put(`/api/crm/activities/${id}/reschedule`, {
    newFollowUpDate,
    newFollowUpTime,
    reason
  });
  return data;
};

export const completeActivity = async (id: number, notes?: string, outcome?: string) => {
  const { data } = await apiClient.put(`/api/crm/activities/${id}/complete`, {
    notes,
    outcome
  });
  return data;
};
```

---

### 7.3 React Follow-ups & Activities Component Example (`ActivitiesPage.tsx`)
```tsx
import React, { useState, useEffect } from 'react';
import {
  getActivitySummary,
  getFollowUps,
  rescheduleActivity,
  completeActivity
} from './crmApi';

export const ActivitiesPage: React.FC = () => {
  const [summary, setSummary] = useState<any>(null);
  const [followUps, setFollowUps] = useState<any[]>([]);
  const [activeBucket, setActiveBucket] = useState<'today' | 'overdue' | 'upcoming' | 'completed'>('today');
  const [loading, setLoading] = useState<boolean>(false);

  const loadData = async () => {
    setLoading(true);
    try {
      const [sumData, fupData] = await Promise.all([
        getActivitySummary(),
        getFollowUps(activeBucket)
      ]);
      setSummary(sumData);
      setFollowUps(fupData.content || fupData);
    } catch (err) {
      console.error('Failed to load activity data', err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadData();
  }, [activeBucket]);

  const handleReschedule = async (activityId: number) => {
    const newDate = prompt('Enter new date (YYYY-MM-DD):');
    if (!newDate) return;
    const reason = prompt('Enter reason for rescheduling:');
    await rescheduleActivity(activityId, newDate, '11:00:00', reason || undefined);
    loadData();
  };

  const handleComplete = async (activityId: number) => {
    const notes = prompt('Enter completion notes:');
    const outcome = prompt('Call outcome (e.g. interested, callback, meeting_scheduled):');
    await completeActivity(activityId, notes || undefined, outcome || undefined);
    loadData();
  };

  return (
    <div style={{ padding: '24px' }}>
      <h1>Activities & Follow-ups</h1>

      {/* KPI Cards */}
      {summary && (
        <div style={{ display: 'flex', gap: '16px', marginBottom: '24px' }}>
          <div style={{ border: '1px solid #ddd', padding: '16px', borderRadius: '8px' }}>
            <h4>Today's Calls</h4>
            <h2>{summary.callsToday}</h2>
          </div>
          <div style={{ border: '1px solid #ddd', padding: '16px', borderRadius: '8px', color: '#d9534f' }}>
            <h4>Overdue Follow-ups</h4>
            <h2>{summary.overdueFollowUps}</h2>
          </div>
          <div style={{ border: '1px solid #ddd', padding: '16px', borderRadius: '8px', color: '#0275d8' }}>
            <h4>Due Today</h4>
            <h2>{summary.followUpsDueToday}</h2>
          </div>
          <div style={{ border: '1px solid #ddd', padding: '16px', borderRadius: '8px', color: '#5cb85c' }}>
            <h4>Completed</h4>
            <h2>{summary.completedFollowUps}</h2>
          </div>
        </div>
      )}

      {/* Tab Navigation */}
      <div style={{ display: 'flex', gap: '8px', marginBottom: '16px' }}>
        {(['today', 'overdue', 'upcoming', 'completed'] as const).map((tab) => (
          <button
            key={tab}
            onClick={() => setActiveBucket(tab)}
            style={{
              padding: '8px 16px',
              backgroundColor: activeBucket === tab ? '#007bff' : '#eee',
              color: activeBucket === tab ? '#fff' : '#000',
              border: 'none',
              borderRadius: '4px',
              cursor: 'pointer'
            }}
          >
            {tab.toUpperCase()}
          </button>
        ))}
      </div>

      {/* Follow-up List Table */}
      {loading ? (
        <p>Loading activities...</p>
      ) : (
        <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left' }}>
          <thead>
            <tr style={{ borderBottom: '2px solid #ccc' }}>
              <th>ID</th>
              <th>Lead / Contact</th>
              <th>Follow-up Date</th>
              <th>Time</th>
              <th>Status</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            {followUps.map((item) => (
              <tr key={item.id} style={{ borderBottom: '1px solid #eee' }}>
                <td>{item.id}</td>
                <td>{item.leadTitle || item.contactName || 'N/A'}</td>
                <td>{item.followUpDate}</td>
                <td>{item.followUpTime || 'Anytime'}</td>
                <td>{item.status}</td>
                <td>
                  {item.status !== 'completed' && (
                    <>
                      <button onClick={() => handleReschedule(item.id)} style={{ marginRight: '8px' }}>
                        Reschedule
                      </button>
                      <button onClick={() => handleComplete(item.id)}>Complete</button>
                    </>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
};
```

---

## 8. API Route Quick Reference Table

| Feature | Method | Path | Request Body | Response Description |
| :--- | :---: | :--- | :--- | :--- |
| **Update Campaign & 5 Rounds** | `PUT` | `/api/crm/campaigns/{id}` | `UpdateCampaignDTO` | Updated campaign with nested 5 rounds |
| **Get All Campaigns** | `GET` | `/api/crm/campaigns/all` | None | Array of all campaigns |
| **Lead Bulk Assign** | `POST` | `/api/crm/leads/bulk-assign` | `{ leadIds, targetOwnerAdminId }` | `BulkOperationResponse` |
| **Lead Bulk Tag** | `POST` | `/api/crm/leads/bulk-tag` | `{ leadIds, tags }` | `BulkOperationResponse` |
| **Lead CSV Import** | `POST` | `/api/crm/leads/import` | `multipart/form-data (file)` | `CsvImportResponse` |
| **Get All Leads** | `GET` | `/api/crm/leads/all` | Query: `paged`, `page`, `size` | Array or PagedResponse |
| **Get All Contacts** | `GET` | `/api/crm/contacts/all` | Query: `paged`, `companyId`, `search` | Array or PagedResponse |
| **Get All Quotations** | `GET` | `/api/crm/quotations/all` | Query: `paged`, `page`, `size` | Array or PagedResponse |
| **All Activities** | `GET` | `/api/crm/activities/all` | Query: `activityType`, `status`, `search` | Filtered activities |
| **Follow-up Buckets** | `GET` | `/api/crm/activities/follow-ups` | Query: `bucket`, `myOnly`, `page`, `size` | Bucket activities (`today`, `overdue`, etc.) |
| **Activity KPI Summary** | `GET` | `/api/crm/activities/summary` | Query: `myOnly` | Summary KPI counts object |
| **Reschedule Follow-up** | `PUT` | `/api/crm/activities/{id}/reschedule` | `{ newFollowUpDate, newFollowUpTime, reason }` | Updated Activity object |
| **Complete Activity** | `PUT` | `/api/crm/activities/{id}/complete` | `{ notes, outcome }` | Completed Activity object |

---

*Generated for the Bentork EV CRM Frontend & Backend Integration.*
