import { describe, it, expect } from 'vitest';
import { HelloAssoLink, HelloAssoWebhookState, KeyCheck } from '@/types/helloasso';
import {
  BadgeTone,
  HelloAssoFlowView,
  HelloAssoPath,
  HelloAssoScreen,
  HelloAssoTabBadge,
  LinkMissing,
  StepState,
  canCollectDonations,
  currentScreen,
  faqVisible,
  formatClock,
  fundraisingPageUrl,
  helloAssoTabBadge,
  isSuspended,
  keysReady,
  linkMissing,
  resolveFlowView,
  stepperItems,
  verificationUrl,
} from '../helloAssoFlow';

const linked = (canCollect: boolean, everCompliant: boolean, webhook: HelloAssoWebhookState = HelloAssoWebhookState.UNKNOWN) => ({
  link: HelloAssoLink.OK,
  canCollect,
  everCompliant,
  webhook,
});
const notLinked = { link: HelloAssoLink.NONE, canCollect: false, everCompliant: false, webhook: HelloAssoWebhookState.UNKNOWN };
const broken = { link: HelloAssoLink.BROKEN, canCollect: false, everCompliant: true, webhook: HelloAssoWebhookState.UNKNOWN };

describe('canCollectDonations / isSuspended', () => {
  it('collects only when linked and eligible', () => {
    expect(canCollectDonations(linked(true, true))).toBe(true);
    expect(canCollectDonations(linked(false, false))).toBe(false);
    expect(canCollectDonations({ ...broken, canCollect: true })).toBe(false);
    expect(canCollectDonations(null)).toBe(false);
  });

  it('is suspended only when linked, not eligible, and eligible before', () => {
    expect(isSuspended(linked(false, true))).toBe(true);
    expect(isSuspended(linked(false, false))).toBe(false);
    expect(isSuspended(linked(true, true))).toBe(false);
    expect(isSuspended(broken)).toBe(false);
  });
});

describe('currentScreen', () => {
  it('keeps the chosen screen while not linked', () => {
    expect(currentScreen(notLinked, HelloAssoScreen.OPEN_ACCOUNT)).toBe(HelloAssoScreen.OPEN_ACCOUNT);
    expect(currentScreen(undefined, HelloAssoScreen.LINK)).toBe(HelloAssoScreen.LINK);
    expect(currentScreen(broken, HelloAssoScreen.LINK)).toBe(HelloAssoScreen.LINK);
  });

  it('derives 5 / 6 from the server once linked', () => {
    expect(currentScreen(linked(false, false), HelloAssoScreen.WELCOME)).toBe(HelloAssoScreen.REVIEW);
    expect(currentScreen(linked(true, true), HelloAssoScreen.WELCOME)).toBe(HelloAssoScreen.ACTIVE);
    expect(currentScreen(linked(false, true), HelloAssoScreen.LINK)).toBe(HelloAssoScreen.ACTIVE);
  });
});

describe('resolveFlowView', () => {
  it('loads until resolved, then shows BROKEN unless re-pasting the key', () => {
    expect(resolveFlowView(false, linked(true, true), false)).toBe(HelloAssoFlowView.LOADING);
    expect(resolveFlowView(true, broken, false)).toBe(HelloAssoFlowView.BROKEN);
    expect(resolveFlowView(true, broken, true)).toBe(HelloAssoFlowView.FLOW);
    expect(resolveFlowView(true, notLinked, false)).toBe(HelloAssoFlowView.FLOW);
  });
});

describe('stepperItems', () => {
  const states = (items: ReturnType<typeof stepperItems>) => items.map((i) => i.state);

  it('maps screens to steps', () => {
    expect(states(stepperItems(HelloAssoScreen.WELCOME, null, notLinked, KeyCheck.IDLE)))
      .toEqual([StepState.CURRENT, StepState.UPCOMING, StepState.UPCOMING, StepState.UPCOMING]);
    expect(states(stepperItems(HelloAssoScreen.OPEN_ACCOUNT, HelloAssoPath.NEW, notLinked, KeyCheck.IDLE))[0])
      .toBe(StepState.CURRENT);
    expect(states(stepperItems(HelloAssoScreen.LINK, HelloAssoPath.NEW, notLinked, KeyCheck.IDLE)))
      .toEqual([StepState.DONE, StepState.CURRENT, StepState.UPCOMING, StepState.UPCOMING]);
    expect(states(stepperItems(HelloAssoScreen.REVIEW, null, linked(false, false), KeyCheck.IDLE)))
      .toEqual([StepState.DONE, StepState.DONE, StepState.CURRENT, StepState.UPCOMING]);
  });

  it('shows step 1 as skipped on the "existing account" path', () => {
    expect(states(stepperItems(HelloAssoScreen.LINK, HelloAssoPath.EXISTING, notLinked, KeyCheck.IDLE)))
      .toEqual([StepState.SKIPPED, StepState.CURRENT, StepState.UPCOMING, StepState.UPCOMING]);
  });

  it('marks every step done once collecting', () => {
    expect(states(stepperItems(HelloAssoScreen.ACTIVE, HelloAssoPath.EXISTING, linked(true, true), KeyCheck.IDLE)))
      .toEqual([StepState.DONE, StepState.DONE, StepState.DONE, StepState.DONE]);
  });

  it('warns on the current step when suspended or when the codes are refused', () => {
    expect(stepperItems(HelloAssoScreen.ACTIVE, null, linked(false, true), KeyCheck.IDLE)[3].warn).toBe(true);
    expect(stepperItems(HelloAssoScreen.LINK, null, notLinked, KeyCheck.MISMATCH)[1].warn).toBe(true);
    expect(stepperItems(HelloAssoScreen.LINK, null, notLinked, KeyCheck.INVALID)[1].warn).toBe(true);
    expect(stepperItems(HelloAssoScreen.LINK, null, notLinked, KeyCheck.OK)[1].warn).toBe(false);
  });
});

describe('helloAssoTabBadge', () => {
  it('follows the brief priority', () => {
    expect(helloAssoTabBadge(broken)).toEqual({ label: HelloAssoTabBadge.BROKEN, tone: BadgeTone.ERROR });
    expect(helloAssoTabBadge(linked(false, true))).toEqual({ label: HelloAssoTabBadge.SUSPENDED, tone: BadgeTone.ERROR });
    expect(helloAssoTabBadge(linked(true, true, HelloAssoWebhookState.MISSING)))
      .toEqual({ label: HelloAssoTabBadge.WEBHOOK_MISSING, tone: BadgeTone.PENDING });
    expect(helloAssoTabBadge(linked(true, true, HelloAssoWebhookState.RECEIVED)))
      .toEqual({ label: HelloAssoTabBadge.ACTIVE, tone: BadgeTone.OK });
    expect(helloAssoTabBadge(linked(false, false))).toEqual({ label: HelloAssoTabBadge.PENDING, tone: BadgeTone.PENDING });
    expect(helloAssoTabBadge(notLinked)).toEqual({ label: HelloAssoTabBadge.NOT_LINKED, tone: BadgeTone.NEUTRAL });
    expect(helloAssoTabBadge(null)).toEqual({ label: HelloAssoTabBadge.NOT_LINKED, tone: BadgeTone.NEUTRAL });
  });
});

describe('linkMissing', () => {
  const ready = { keyCheck: KeyCheck.OK, verifyId: 'v1', orgConfirmed: true, webhookAck: true, mandateAccepted: true };

  it('is empty when everything is done', () => {
    expect(linkMissing(ready)).toEqual([]);
  });

  it('lists what remains, in order', () => {
    expect(linkMissing({ ...ready, keyCheck: KeyCheck.IDLE, webhookAck: false, mandateAccepted: false }))
      .toEqual([LinkMissing.KEYS, LinkMissing.WEBHOOK_ACK, LinkMissing.MANDATE]);
    expect(linkMissing({ ...ready, orgConfirmed: false })).toEqual([LinkMissing.ORG_CONFIRM]);
  });

  it('never lets another association or a missing handle through', () => {
    expect(linkMissing({ ...ready, keyCheck: KeyCheck.MISMATCH })).toEqual([LinkMissing.KEYS]);
    expect(linkMissing({ ...ready, verifyId: null })).toEqual([LinkMissing.KEYS]);
  });
});

describe('helpers', () => {
  it('shows the FAQ on screens 0, 5 and 6 only', () => {
    expect([0, 3, 4, 5, 6].map((s) => faqVisible(s as HelloAssoScreen))).toEqual([true, false, false, true, true]);
  });

  it('mirrors the backend key constraints', () => {
    expect(keysReady(' id ', ' secret ')).toBe(true);
    expect(keysReady('id', '   ')).toBe(false);
    expect(keysReady('x'.repeat(256), 'secret')).toBe(false);
  });

  it('opens the French fundraising page only when it can show a campaign', () => {
    expect(fundraisingPageUrl({ widgetToken: 'clk_1', widgetDestinationCampaignId: 'c1' })).toBe('/fr/lp/clk_1');
    expect(fundraisingPageUrl({ widgetToken: 'clk_1', widgetDestinationCampaignId: null })).toBeNull();
    expect(fundraisingPageUrl({ widgetToken: null, widgetDestinationCampaignId: 'c1' })).toBeNull();
    expect(fundraisingPageUrl(null)).toBeNull();
  });

  it('builds the verification link from the slug', () => {
    expect(verificationUrl('les-amis')).toBe('https://admin.helloasso.com/les-amis/verification');
  });

  it('formats a clock time, null when absent', () => {
    expect(formatClock(null, 'fr')).toBeNull();
    expect(formatClock('not a date', 'fr')).toBeNull();
    expect(formatClock('2026-11-03T09:41:00Z', 'fr')).toMatch(/^\d{2}:\d{2}$/);
  });
});
