import React, { Component } from 'react';
import * as R from 'ramda';
import { DomainOutlined } from '@mui/icons-material';
import Box from '@mui/material/Box';
import { withStyles } from '@mui/styles';
import { connect } from 'react-redux';
import Dialog from '@mui/material/Dialog';
import DialogTitle from '@mui/material/DialogTitle';
import DialogContent from '@mui/material/DialogContent';
import OrganizationForm from '../private/components/organizations/OrganizationForm';
import { fetchOrganizations, addOrganization } from '../actions/Organization';
import { Autocomplete } from './Autocomplete';
import inject18n from './i18n';
import { storeBrowser } from '../actions/Schema';

const styles = () => ({
  icon: {
    paddingTop: 4,
    display: 'inline-block',
  },
  text: {
    display: 'inline-block',
    flexGrow: 1,
    marginLeft: 10,
  },
  autoCompleteIndicator: {
    display: 'none',
  },
});

class OrganizationField extends Component {
  constructor(props) {
    super(props);
    this.state = { organizationCreation: false, organizationInput: '' };
  }

  componentDidMount() {
    this.props.fetchOrganizations();
  }

  handleOpenOrganizationCreation() {
    this.setState({ organizationCreation: true });
  }

  handleCloseOrganizationCreation() {
    this.setState({ organizationCreation: false });
  }

  onSubmit(data) {
    const { name, setFieldValue } = this.props;
    const inputValues = R.pipe(
      R.assoc('organization_tags', R.pluck('id', data.organization_tags)),
    )(data);
    this.props.addOrganization(inputValues).then((result) => {
      if (result.result) {
        const newOrganization = result.entities.organizations[result.result];
        const organization = {
          id: newOrganization.organization_id,
          label: newOrganization.organization_name,
        };
        setFieldValue(name, organization);
        return this.handleCloseOrganizationCreation();
      }
      return result;
    });
  }

  render() {
    const {
      t, name, organizations, classes,
    } = this.props;
    const organizationsOptions = R.map(
      (n) => ({
        id: n.organization_id,
        label: n.organization_name,
      }),
      organizations,
    );
    return (
      <div>
        <Autocomplete
          variant="standard"
          size="small"
          name={name}
          fullWidth={true}
          multiple={false}
          label={t('Organization')}
          options={organizationsOptions}
          style={{ marginTop: 20 }}
          openCreate={this.handleOpenOrganizationCreation.bind(this)}
          renderOption={(props, option) => (
            <Box component="li" {...props}>
              <div className={classes.icon}>
                <DomainOutlined />
              </div>
              <div className={classes.text}>{option.label}</div>
            </Box>
          )}
          classes={{ clearIndicator: classes.autoCompleteIndicator }}
        />
        <Dialog
          open={this.state.organizationCreation}
          onClose={this.handleCloseOrganizationCreation.bind(this)}
        >
          <DialogTitle>{t('Create a new organization')}</DialogTitle>
          <DialogContent>
            <OrganizationForm
              onSubmit={this.onSubmit.bind(this)}
              initialValues={{ organization_tags: [] }}
              handleClose={this.handleCloseOrganizationCreation.bind(this)}
            />
          </DialogContent>
        </Dialog>
      </div>
    );
  }
}

const select = (state) => {
  const browser = storeBrowser(state);
  return {
    organizations: browser.organizations,
  };
};

export default R.compose(
  connect(select, { fetchOrganizations, addOrganization }),
  inject18n,
  withStyles(styles),
)(OrganizationField);
