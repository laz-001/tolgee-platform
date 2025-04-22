import { useState } from 'react';
import { useWindowSize } from 'usehooks-ts';
import type { useQuickStartGuideService } from './useQuickStartGuideService';

export const TOP_BAR_HEIGHT = 52;

type Props = {
  quickStart: ReturnType<typeof useQuickStartGuideService>;
  aiPlaygroundEnabled: boolean;
};

export const useLayoutService = ({
  quickStart,
  aiPlaygroundEnabled,
}: Props) => {
  const [topBannerHeight, setTopBannerHeight] = useState(0);
  const [topSubBannerHeight, setTopSubBannerHeight] = useState(0);
  const [topBarHidden, setTopBarHidden] = useState(false);
  const viewPortSize = useWindowSize();

  const bodyWidth = viewPortSize.width;

  const rightPanelHidden =
    bodyWidth < 1200 ||
    ((!quickStart.state.enabled ||
      !quickStart.state.open ||
      quickStart.state.floatingForced) &&
      !aiPlaygroundEnabled);

  const quickStartFloating =
    quickStart.state.enabled &&
    (quickStart.state.floatingForced || aiPlaygroundEnabled);

  const desiredWidth = aiPlaygroundEnabled ? Math.max(400, bodyWidth / 3) : 400;

  const rightPanelWidth = !rightPanelHidden
    ? Math.min(desiredWidth, bodyWidth)
    : 0;

  const state = {
    topBannerHeight,
    topSubBannerHeight,
    bodyWidth,
    rightPanelWidth,
    quickStartFloating,
    topBarHeight: topBarHidden ? 0 : TOP_BAR_HEIGHT,
  };

  const actions = {
    setTopBannerHeight,
    setTopSubBannerHeight,
    setTopBarHidden,
  };

  return { state, actions };
};
