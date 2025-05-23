import { Box } from '@mui/material';
import React, { FC } from 'react';
import { CloudPlanPricesAndLimits } from './CloudPlanPricesAndLimits';
import { PlanNonCommercialSwitch } from '../../genericFields/PlanNonCommercialSwitch';
import { CloudPlanTypeSelectField } from './CloudPlanTypeSelectField';
import { PlanStripeProductSelectField } from '../../genericFields/PlanStripeProductSelectField';
import { CloudPlanMetricTypeSelectField } from './CloudPlanMetricTypeSelectField';
import { PlanEnabledFeaturesField } from '../../genericFields/PlanEnabledFeaturesField';
import { PlanFreePlanSwitch } from '../../genericFields/PlanFreePlanSwitch';
import { PlanNameField } from '../../genericFields/PlanNameField';

export const CloudPlanFields: FC<{
  parentName?: string;
  isUpdate?: boolean;
  canEditPrices: boolean;
}> = ({ parentName, isUpdate, canEditPrices }) => {
  parentName = parentName ? parentName + '.' : '';

  return (
    <>
      <PlanNameField />

      <PlanFreePlanSwitch isUpdate={isUpdate} />

      <Box
        sx={{
          display: 'grid',
          gap: 2,
          mt: 2,
          gridTemplateColumns: '1fr 1fr 1fr',
        }}
      >
        <CloudPlanTypeSelectField parentName={parentName} />
        <CloudPlanMetricTypeSelectField parentName={parentName} />
        <PlanStripeProductSelectField parentName={parentName} />
      </Box>

      <CloudPlanPricesAndLimits
        parentName={parentName}
        canEditPrices={canEditPrices}
      />

      <PlanEnabledFeaturesField parentName={parentName} />
      <PlanNonCommercialSwitch />
    </>
  );
};
